package com.glimmer.feed.service;

import java.util.Collection;
import java.util.List;

/**
 * 关注流 Redis 收件箱读写。
 *
 * <p>收件箱是 cache Redis 里的可丢缓存：
 * <ul>
 *   <li>{@code glimmer:feed:inbox:{userId}}：ZSet，member=postId，score=publishedAt 毫秒；</li>
 *   <li>{@code glimmer:feed:last-read:{userId}}：最近一次读关注流的时间，用于首页重建判断。</li>
 * </ul>
 *
 * <p>写入失败要让调用方感知：写扩散消费者需要 nack 进死信队列；读失败由
 * FeedService 回源 MySQL 兜底。
 */
public interface FeedInboxService {

    /** 收件箱中的一条帖子记录。 */
    record InboxEntry(long postId, long publishedAtMillis) {
    }

    /** 待写入收件箱的帖子。 */
    record InboxPost(long postId, long publishedAtMillis) {
    }

    /**
     * 把一篇帖子写入多个用户的收件箱；内部用 pipeline 批量执行。
     *
     * @param userIds           目标用户 ID
     * @param postId            帖子 ID
     * @param publishedAtMillis 发布时间，epoch 毫秒
     */
    void addPostToInboxes(Collection<Long> userIds, long postId, long publishedAtMillis);

    /**
     * 把多篇帖子写入一个用户的收件箱，供关注回填和首页重建使用。
     *
     * @param userId 目标用户 ID
     * @param posts  待写入帖子
     */
    void addPostsToInbox(Long userId, Collection<InboxPost> posts);

    /**
     * 读取收件箱，按 score 从高到低返回，最多 {@code glimmer.feed.inbox-max-size} 条。
     *
     * @param userId 用户 ID
     * @return 收件箱记录；为空时返回空列表
     */
    List<InboxEntry> read(Long userId);

    /**
     * 判断首页是否需要重建：收件箱为空、从未读过，或距上次读已超过配置时间。
     *
     * @param userId 用户 ID
     * @return 需要重建返回 {@code true}
     */
    boolean shouldRebuild(Long userId);

    /**
     * 清空并重建收件箱；写失败时下一次读会回源 MySQL。
     *
     * @param userId 用户 ID
     * @param posts  重建用的最近帖子
     */
    void rebuild(Long userId, Collection<InboxPost> posts);

    /**
     * 记录一次关注流读取时间；失败只记日志，不影响读取结果。
     *
     * @param userId 用户 ID
     */
    void markRead(Long userId);
}
