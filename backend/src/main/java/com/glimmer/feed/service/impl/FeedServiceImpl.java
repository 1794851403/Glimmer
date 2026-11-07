package com.glimmer.feed.service.impl;

import com.glimmer.common.api.CursorCodec;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.feed.service.FeedInboxService;
import com.glimmer.feed.service.FeedInboxService.InboxEntry;
import com.glimmer.feed.service.FeedInboxService.InboxPost;
import com.glimmer.feed.service.FeedService;
import com.glimmer.user.service.UserActivityService;
import com.glimmer.user.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关注流实现：收件箱优先，收件箱覆盖不了一页时回源 MySQL。
 *
 * <p>收件箱最多保留 200 条，且可能包含已取关作者、已下架 / 已删除帖子。
 * 因此读路径先按游标过滤、排序，再分块回填摘要；只有凑满一页 + 1 条可见
 * 帖子时才直接返回，否则用 {@link PostService#listByAuthors} 拉同一游标的一页，
 * 避免因为收件箱被裁剪而提前结束分页。
 */
@Slf4j
@Service
public class FeedServiceImpl implements FeedService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;
    private static final int HYDRATE_CHUNK = 50;
    private static final int REBUILD_BACKFILL_SIZE = 50;

    private final UserService userService;
    private final PostService postService;
    private final FeedInboxService feedInboxService;
    private final UserActivityService userActivityService;

    public FeedServiceImpl(UserService userService,
                           PostService postService,
                           FeedInboxService feedInboxService,
                           UserActivityService userActivityService) {
        this.userService = userService;
        this.postService = postService;
        this.feedInboxService = feedInboxService;
        this.userActivityService = userActivityService;
    }

    @Override
    public CursorPage<PostSummaryVO> followFeed(String cursor, Integer size) {
        Long userId = UserContext.userId();
        // 读关注流也算活跃，保证长期不重新登录的用户仍能被写扩散覆盖。
        userActivityService.markActive(userId);

        List<Long> followingIds = userService.listFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return CursorPage.empty();
        }
        Set<Long> followingSet = new HashSet<>(followingIds);
        int pageSize = pageSize(size);
        CursorCodec.CursorValue cursorValue = CursorCodec.decode(cursor);
        boolean firstPage = cursorValue == null;

        try {
            if (firstPage && feedInboxService.shouldRebuild(userId)) {
                rebuildInbox(userId, followingIds);
            }
            CursorPage<PostSummaryVO> inboxPage = readInboxPage(
                    userId, cursorValue, pageSize, followingSet);
            if (inboxPage != null) {
                feedInboxService.markRead(userId);
                return inboxPage;
            }
        } catch (DataAccessException e) {
            // 收件箱不可用不能影响关注流；直接回源 MySQL。
            log.warn("feed inbox unavailable, fallback to DB, userId={}", userId, e);
        }

        CursorPage<PostSummaryVO> fallback = postService.listByAuthors(followingIds, cursor, size);
        feedInboxService.markRead(userId);
        return fallback;
    }

    /**
     * 首页重建：清空旧收件箱，回源拉关注作者最近 50 条公开帖子重新写入。
     */
    private void rebuildInbox(Long userId, List<Long> followingIds) {
        CursorPage<PostSummaryVO> latest = postService.listByAuthors(
                followingIds, null, REBUILD_BACKFILL_SIZE);
        feedInboxService.rebuild(userId, toInboxPosts(latest));
    }

    /**
     * 从收件箱尝试凑一页；凑不满一页时返回 {@code null}，由调用方回源 MySQL。
     */
    private CursorPage<PostSummaryVO> readInboxPage(Long userId,
                                                    CursorCodec.CursorValue cursor,
                                                    int pageSize,
                                                    Set<Long> followingSet) {
        List<InboxEntry> entries = feedInboxService.read(userId);
        if (entries.isEmpty()) {
            return null;
        }

        long cursorMillis = cursor == null ? Long.MAX_VALUE : cursor.epochMilli();
        long cursorId = cursor == null ? Long.MAX_VALUE : cursor.id();
        Comparator<InboxEntry> order = Comparator
                .comparingLong(InboxEntry::publishedAtMillis)
                .thenComparingLong(InboxEntry::postId)
                .reversed();
        List<InboxEntry> candidates = entries.stream()
                .filter(entry -> entry.publishedAtMillis() < cursorMillis
                        || (entry.publishedAtMillis() == cursorMillis && entry.postId() < cursorId))
                .sorted(order)
                .toList();
        if (candidates.isEmpty()) {
            return null;
        }

        List<PostSummaryVO> visible = new ArrayList<>(pageSize + 1);
        for (int from = 0; from < candidates.size() && visible.size() <= pageSize; from += HYDRATE_CHUNK) {
            int to = Math.min(from + HYDRATE_CHUNK, candidates.size());
            List<InboxEntry> chunk = candidates.subList(from, to);
            List<Long> postIds = chunk.stream().map(InboxEntry::postId).toList();
            Map<Long, PostSummaryVO> summaries = postService.listSummariesByIds(postIds);
            if (summaries == null || summaries.isEmpty()) {
                continue;
            }
            for (InboxEntry entry : chunk) {
                PostSummaryVO summary = summaries.get(entry.postId());
                if (summary == null) {
                    // 已删除、审核拒绝、已下架等不可见帖子。
                    continue;
                }
                Long authorId = parseId(summary.author() == null ? null : summary.author().id());
                if (authorId == null || !followingSet.contains(authorId)
                        || authorId.equals(UserContext.userId())) {
                    // 已取关或异常作者，读时过滤。
                    continue;
                }
                visible.add(summary);
                if (visible.size() > pageSize) {
                    break;
                }
            }
        }

        if (visible.size() <= pageSize) {
            // 收件箱无法证明这一页是完整的，回源 MySQL 补足。
            return null;
        }
        List<PostSummaryVO> items = new ArrayList<>(visible.subList(0, pageSize));
        PostSummaryVO last = items.get(items.size() - 1);
        Long lastId = parseId(last.id());
        String nextCursor = lastId == null
                ? null : CursorCodec.encodeMillis(last.publishedAt(), lastId);
        if (nextCursor == null) {
            return null;
        }
        return new CursorPage<>(items, nextCursor, true);
    }

    private List<InboxPost> toInboxPosts(CursorPage<PostSummaryVO> page) {
        if (page == null || page.items() == null || page.items().isEmpty()) {
            return List.of();
        }
        List<InboxPost> posts = new ArrayList<>(page.items().size());
        for (PostSummaryVO item : page.items()) {
            Long postId = parseId(item.id());
            if (postId != null && item.publishedAt() > 0) {
                posts.add(new InboxPost(postId, item.publishedAt()));
            }
        }
        return posts;
    }

    private int pageSize(Integer rawSize) {
        return rawSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(rawSize, MAX_PAGE_SIZE));
    }

    private Long parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(raw.strip());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
