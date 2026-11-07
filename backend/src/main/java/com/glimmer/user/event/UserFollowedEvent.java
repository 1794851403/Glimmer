package com.glimmer.user.event;

/**
 * 用户关注成功事件。
 *
 * <p>只在真正插入关注关系后发布，重复关注不会发布。Feed 模块用
 * {@code @TransactionalEventListener(AFTER_COMMIT)} 接收，给关注者回填被关注者
 * 最近的公开帖子。user 模块不依赖 feed 模块。
 *
 * @param followerId 关注者用户 ID
 * @param followeeId 被关注者用户 ID
 */
public record UserFollowedEvent(Long followerId, Long followeeId) {
}
