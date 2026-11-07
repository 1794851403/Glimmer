package com.glimmer.feed.service.impl;

import com.glimmer.feed.config.FeedProperties;
import com.glimmer.feed.mq.PostPublishedMessage;
import com.glimmer.feed.service.FeedFanoutService;
import com.glimmer.feed.service.FeedInboxService;
import com.glimmer.user.service.UserActivityService;
import com.glimmer.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 写扩散实现：按 follower_id 游标分批查粉丝，过滤活跃用户后批量写收件箱。
 */
@Slf4j
@Service
public class FeedFanoutServiceImpl implements FeedFanoutService {

    private final UserService userService;
    private final UserActivityService userActivityService;
    private final FeedInboxService feedInboxService;
    private final FeedProperties properties;

    public FeedFanoutServiceImpl(UserService userService,
                                 UserActivityService userActivityService,
                                 FeedInboxService feedInboxService,
                                 FeedProperties properties) {
        this.userService = userService;
        this.userActivityService = userActivityService;
        this.feedInboxService = feedInboxService;
        this.properties = properties;
    }

    @Override
    public void fanout(PostPublishedMessage message) {
        if (message == null || message.postId() == null || message.authorId() == null
                || message.publishedAtMillis() <= 0) {
            return;
        }
        // 只有公开可见的 0/1 状态需要扩散；后续状态变化由读路径过滤。
        if (message.status() != 0 && message.status() != 1) {
            return;
        }

        int batchSize = properties.batchSize();
        long afterFollowerId = 0L;
        int pushed = 0;

        while (true) {
            List<Long> followers = userService.listFollowerIds(
                    message.authorId(), afterFollowerId, batchSize);
            if (followers == null || followers.isEmpty()) {
                break;
            }

            Set<Long> active = userActivityService.filterActiveUserIds(followers);
            if (!active.isEmpty()) {
                feedInboxService.addPostToInboxes(
                        active, message.postId(), message.publishedAtMillis());
                pushed += active.size();
            }

            long next = followers.get(followers.size() - 1);
            if (next <= afterFollowerId) {
                // 防御游标异常，避免死循环。
                log.warn("follower cursor not moving, authorId={}, cursor={}",
                        message.authorId(), afterFollowerId);
                break;
            }
            afterFollowerId = next;
            if (followers.size() < batchSize) {
                break;
            }
        }

        log.info("feed fanout done, postId={}, authorId={}, pushed={}",
                message.postId(), message.authorId(), pushed);
    }
}
