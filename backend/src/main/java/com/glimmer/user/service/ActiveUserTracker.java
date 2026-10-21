package com.glimmer.user.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * glimmer:user:active：最近登录用户 ZSet，用于 Feed 写扩散裁剪。
 *
 * <p>这是可丢缓存：LRU 淘汰或 Redis 重启后，丢失的成员按“不活跃”处理，
 * 用户读 Feed 时由回源重建兜底，不影响内容正确性。
 */
@Slf4j
@Component
public class ActiveUserTracker {

    static final String KEY = "glimmer:user:active";
    private static final Duration ACTIVE_WINDOW = Duration.ofDays(30);

    private final StringRedisTemplate cacheRedisTemplate;

    public ActiveUserTracker(@Qualifier("cacheRedisTemplate") StringRedisTemplate cacheRedisTemplate) {
        this.cacheRedisTemplate = cacheRedisTemplate;
    }

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
