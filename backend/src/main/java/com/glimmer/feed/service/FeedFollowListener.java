package com.glimmer.feed.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.user.event.UserFollowedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * 关注成功后的收件箱回填。
 *
 * <p>关注关系提交后，把被关注者最近 50 条公开帖子写进关注者收件箱。
 * 走本地 Spring 事件而不是 MQ：量小、无需削峰，失败只影响这次历史回填，
 * 后续新帖仍会正常扩散，关注接口不受影响。
 */
@Slf4j
@Component
public class FeedFollowListener {

    private static final int BACKFILL_SIZE = 50;

    private final PostService postService;
    private final FeedInboxService feedInboxService;

    public FeedFollowListener(PostService postService, FeedInboxService feedInboxService) {
        this.postService = postService;
        this.feedInboxService = feedInboxService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserFollowed(UserFollowedEvent event) {
        if (event == null || event.followerId() == null || event.followeeId() == null) {
            return;
        }
        try {
            CursorPage<PostSummaryVO> latest = postService.listByAuthors(
                    List.of(event.followeeId()), null, BACKFILL_SIZE);
            if (latest == null || latest.items().isEmpty()) {
                return;
            }
            List<FeedInboxService.InboxPost> posts = new ArrayList<>(latest.items().size());
            for (PostSummaryVO item : latest.items()) {
                try {
                    long postId = Long.parseLong(item.id());
                    if (postId > 0 && item.publishedAt() > 0) {
                        posts.add(new FeedInboxService.InboxPost(postId, item.publishedAt()));
                    }
                } catch (NumberFormatException ignored) {
                    // 帖子 ID 异常时跳过，不影响其他回填。
                }
            }
            if (!posts.isEmpty()) {
                feedInboxService.addPostsToInbox(event.followerId(), posts);
            }
        } catch (Exception e) {
            // 关注已经成功，回填失败只记日志；读路径仍可回源。
            log.warn("backfill feed inbox failed, followerId={}, followeeId={}",
                    event.followerId(), event.followeeId(), e);
        }
    }
}
