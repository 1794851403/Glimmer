package com.glimmer.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * glimmer:user:active：最近登录 / 读关注流用户 ZSet，用于 Feed 写扩散裁剪。
 *
 * <p>这是可丢缓存：LRU 淘汰或 Redis 重启后，丢失的成员按“不活跃”处理，
 * 用户读 Feed 时由回源重建兜底，不影响内容正确性。活跃集合整体为空或 Redis
 * 读取失败时，筛选方法会 fail-open 返回全部候选，避免缓存故障导致全站停推。
 */
@Slf4j
@Component
public class ActiveUserTracker implements UserActivityService {

    static final String KEY = "glimmer:user:active";
    private static final Duration ACTIVE_WINDOW = Duration.ofDays(30);

    private final StringRedisTemplate cacheRedisTemplate;

    public ActiveUserTracker(@Qualifier("cacheRedisTemplate") StringRedisTemplate cacheRedisTemplate) {
        this.cacheRedisTemplate = cacheRedisTemplate;
    }

    @Override
    public void markActive(Long userId) {
        try {
            cacheRedisTemplate.opsForZSet()
                    .add(KEY, String.valueOf(userId), System.currentTimeMillis());
        } catch (DataAccessException e) {
            // 活跃标记只影响推模式覆盖率，登录不能因此失败。
            log.warn("mark active user failed, userId={}", userId, e);
        }
    }

    /**
     * 先看活跃集合是否整体为空；为空时直接返回全部候选，避免 Redis 重启后
     * 全站用户都被判为不活跃。非空时用 pipeline 批量 ZSCORE，一次网络往返
     * 拿回整批结果。
     *
     * @param userIds 候选用户 ID 集合
     * @return 活跃用户 ID 集合；Redis 异常时返回全部候选
     */
    @Override
    public Set<Long> filterActiveUserIds(Collection<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        List<Long> ids = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Set.of();
        }

        try {
            Long total = cacheRedisTemplate.opsForZSet().zCard(KEY);
            if (total == null || total == 0) {
                return new LinkedHashSet<>(ids);
            }

            List<Object> scores = cacheRedisTemplate.executePipelined(
                    (RedisCallback<Object>) connection -> {
                        byte[] key = KEY.getBytes(StandardCharsets.UTF_8);
                        for (Long id : ids) {
                            connection.zSetCommands().zScore(key,
                                    String.valueOf(id).getBytes(StandardCharsets.UTF_8));
                        }
                        return null;
                    });
            if (scores == null || scores.size() != ids.size()) {
                return new LinkedHashSet<>(ids);
            }

            Set<Long> active = new LinkedHashSet<>();
            for (int i = 0; i < ids.size(); i++) {
                if (scores.get(i) != null) {
                    active.add(ids.get(i));
                }
            }
            return active;
        } catch (DataAccessException e) {
            // 筛选只是写扩散裁剪，失败时按全部活跃处理，宁可多写。
            log.warn("filter active users failed, treat all as active", e);
            return new LinkedHashSet<>(ids);
        }
    }

    /**
     * 每日裁剪一次 30 天前的成员；任务幂等，无需分布式锁。
     */
    @Scheduled(cron = "0 30 3 * * *")
    public void trimInactive() {
        long cutoff = System.currentTimeMillis() - ACTIVE_WINDOW.toMillis();
        try {
            Long removed = cacheRedisTemplate.opsForZSet().removeRangeByScore(KEY, 0, cutoff);
            if (removed != null && removed > 0) {
                log.info("trimmed {} inactive users from {}", removed, KEY);
            }
        } catch (DataAccessException e) {
            log.warn("trim active users failed", e);
        }
    }
}
