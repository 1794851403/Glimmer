package com.glimmer.feed.service.impl;

import com.glimmer.feed.config.FeedProperties;
import com.glimmer.feed.service.FeedInboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 收件箱读写实现。
 *
 * <p>写入用 pipeline 把一批用户的 ZADD / TRIM / EXPIRE 合并成一次网络往返；
 * pipeline 不保证原子性，但 ZADD 幂等、TRIM 只保留最新 N 条，重复消费不会写脏。
 */
@Slf4j
@Service
public class FeedInboxServiceImpl implements FeedInboxService {

    private static final String INBOX_PREFIX = "glimmer:feed:inbox:";
    private static final String LAST_READ_PREFIX = "glimmer:feed:last-read:";

    private final StringRedisTemplate cacheRedisTemplate;
    private final FeedProperties properties;

    public FeedInboxServiceImpl(
            @Qualifier("cacheRedisTemplate") StringRedisTemplate cacheRedisTemplate,
            FeedProperties properties) {
        this.cacheRedisTemplate = cacheRedisTemplate;
        this.properties = properties;
    }

    @Override
    public void addPostToInboxes(Collection<Long> userIds, long postId, long publishedAtMillis) {
        if (userIds == null || userIds.isEmpty() || postId <= 0 || publishedAtMillis <= 0) {
            return;
        }
        List<Long> ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) {
            return;
        }
        write(ids, List.of(new InboxPost(postId, publishedAtMillis)));
    }

    @Override
    public void addPostsToInbox(Long userId, Collection<InboxPost> posts) {
        if (userId == null || posts == null || posts.isEmpty()) {
            return;
        }
        List<InboxPost> items = posts.stream()
                .filter(Objects::nonNull)
                .filter(post -> post.postId() > 0 && post.publishedAtMillis() > 0)
                .toList();
        if (items.isEmpty()) {
            return;
        }
        write(List.of(userId), items);
    }

    @Override
    public List<InboxEntry> read(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = cacheRedisTemplate.opsForZSet()
                .reverseRangeWithScores(inboxKey(userId), 0, properties.maxSize() - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<InboxEntry> entries = new ArrayList<>(tuples.size());
        for (ZSetOperations.TypedTuple<String> tuple : tuples) {
            if (tuple == null || tuple.getValue() == null || tuple.getScore() == null) {
                continue;
            }
            try {
                long postId = Long.parseLong(tuple.getValue());
                if (postId > 0) {
                    entries.add(new InboxEntry(postId, Math.round(tuple.getScore())));
                }
            } catch (NumberFormatException ignored) {
                // 非法 member 直接跳过，不阻塞整个收件箱。
            }
        }
        return entries;
    }

    @Override
    public boolean shouldRebuild(Long userId) {
        if (userId == null) {
            return true;
        }
        Long size = cacheRedisTemplate.opsForZSet().zCard(inboxKey(userId));
        if (size == null || size == 0) {
            return true;
        }
        String lastRead = cacheRedisTemplate.opsForValue().get(lastReadKey(userId));
        if (lastRead == null || lastRead.isBlank()) {
            return true;
        }
        try {
            long lastReadAt = Long.parseLong(lastRead);
            return System.currentTimeMillis() - lastReadAt > properties.rebuildAfter().toMillis();
        } catch (NumberFormatException e) {
            return true;
        }
    }

    @Override
    public void rebuild(Long userId, Collection<InboxPost> posts) {
        if (userId == null) {
            return;
        }
        cacheRedisTemplate.delete(inboxKey(userId));
        addPostsToInbox(userId, posts);
    }

    @Override
    public void markRead(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            cacheRedisTemplate.opsForValue().set(
                    lastReadKey(userId),
                    String.valueOf(System.currentTimeMillis()),
                    properties.ttl());
        } catch (DataAccessException e) {
            // 读取时间只影响下次是否重建，不能影响本次响应。
            log.warn("mark feed read failed, userId={}", userId, e);
        }
    }

    /**
     * 批量写收件箱。所有用户的命令放在同一个 pipeline 中，减少网络往返；
     * 单个用户的 ZADD / TRIM / EXPIRE 之间允许其他命令穿插，最终仍是“最新 N 条”。
     */
    private void write(Collection<Long> userIds, Collection<InboxPost> posts) {
        List<InboxPost> items = posts.stream().filter(Objects::nonNull).toList();
        if (items.isEmpty()) {
            return;
        }
        long ttlSeconds = properties.ttl().toSeconds();
        long maxSize = properties.maxSize();
        cacheRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (Long userId : userIds) {
                if (userId == null) {
                    continue;
                }
                byte[] key = inboxKey(userId).getBytes(StandardCharsets.UTF_8);
                for (InboxPost post : items) {
                    byte[] member = String.valueOf(post.postId()).getBytes(StandardCharsets.UTF_8);
                    connection.zSetCommands().zAdd(key, post.publishedAtMillis(), member);
                }
                // 保留 score 最高的 maxSize 条，即最新帖子。
                connection.zSetCommands().zRemRange(key, 0L, -(maxSize + 1));
                connection.expire(key, ttlSeconds);
            }
            return null;
        });
    }

    private String inboxKey(Long userId) {
        return INBOX_PREFIX + userId;
    }

    private String lastReadKey(Long userId) {
        return LAST_READ_PREFIX + userId;
    }
}
