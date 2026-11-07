package com.glimmer.feed.service.impl;

import com.glimmer.common.api.CursorCodec;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.feed.service.FeedInboxService;
import com.glimmer.feed.service.FeedInboxService.InboxEntry;
import com.glimmer.feed.service.FeedInboxService.InboxPost;
import com.glimmer.user.service.UserActivityService;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.UserBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedServiceImplTest {

    private UserService userService;
    private PostService postService;
    private FeedInboxService feedInboxService;
    private UserActivityService userActivityService;
    private FeedServiceImpl feedService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        postService = mock(PostService.class);
        feedInboxService = mock(FeedInboxService.class);
        userActivityService = mock(UserActivityService.class);
        feedService = new FeedServiceImpl(
                userService, postService, feedInboxService, userActivityService);
        UserContext.set(new LoginUser(1L, "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldReturnEmptyPageWhenFollowingNobody() {
        when(userService.listFollowingIds(1L)).thenReturn(List.of());

        CursorPage<PostSummaryVO> page = feedService.followFeed(null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
        assertThat(page.nextCursor()).isNull();
        verify(userActivityService).markActive(1L);
        verifyNoInteractions(postService, feedInboxService);
    }

    @Test
    void shouldFallbackToDatabaseForBadCursorWhenInboxEmpty() {
        List<Long> followingIds = List.of(2L, 3L);
        CursorPage<PostSummaryVO> expected = new CursorPage<>(
                List.of(summary("1001", 2L, 1761700800000L)), "next-cursor", true);
        when(userService.listFollowingIds(1L)).thenReturn(followingIds);
        when(feedInboxService.shouldRebuild(1L)).thenReturn(false);
        when(feedInboxService.read(1L)).thenReturn(List.of());
        when(postService.listByAuthors(followingIds, "cursor", 10)).thenReturn(expected);

        CursorPage<PostSummaryVO> page = feedService.followFeed("cursor", 10);

        assertThat(page).isSameAs(expected);
        verify(postService).listByAuthors(followingIds, "cursor", 10);
        verify(feedInboxService).markRead(1L);
    }

    @Test
    void shouldReturnInboxPageWhenItCanProveFullPage() {
        when(userService.listFollowingIds(1L)).thenReturn(List.of(2L));
        when(feedInboxService.shouldRebuild(1L)).thenReturn(false);
        when(feedInboxService.read(1L)).thenReturn(List.of(
                new InboxEntry(100L, 3000L),
                new InboxEntry(101L, 2000L),
                new InboxEntry(102L, 1000L)));
        when(postService.listSummariesByIds(any())).thenReturn(summaries(
                summary("100", 2L, 3000L),
                summary("101", 2L, 2000L),
                summary("102", 2L, 1000L)));

        CursorPage<PostSummaryVO> page = feedService.followFeed(null, 2);

        assertThat(page.items()).extracting(PostSummaryVO::id)
                .containsExactly("100", "101");
        assertThat(page.hasMore()).isTrue();
        CursorCodec.CursorValue next = CursorCodec.decode(page.nextCursor());
        assertThat(next).isNotNull();
        assertThat(next.id()).isEqualTo(101L);
        assertThat(next.epochMilli()).isEqualTo(2000L);
        verify(postService, never()).listByAuthors(any(), any(), any());
        verify(feedInboxService).markRead(1L);
    }

    @Test
    void shouldFallbackWhenInboxCannotFillPage() {
        List<Long> followingIds = List.of(2L);
        CursorPage<PostSummaryVO> expected = new CursorPage<>(
                List.of(summary("100", 2L, 3000L)), "next", true);
        when(userService.listFollowingIds(1L)).thenReturn(followingIds);
        when(feedInboxService.shouldRebuild(1L)).thenReturn(false);
        when(feedInboxService.read(1L)).thenReturn(List.of(
                new InboxEntry(100L, 3000L),
                new InboxEntry(101L, 2000L)));
        when(postService.listSummariesByIds(any())).thenReturn(summaries(
                summary("100", 2L, 3000L),
                summary("101", 2L, 2000L)));
        when(postService.listByAuthors(followingIds, null, 2)).thenReturn(expected);

        CursorPage<PostSummaryVO> page = feedService.followFeed(null, 2);

        assertThat(page).isSameAs(expected);
        verify(postService).listByAuthors(followingIds, null, 2);
    }

    @Test
    void shouldFallbackWhenRedisFails() {
        List<Long> followingIds = List.of(2L);
        CursorPage<PostSummaryVO> expected = new CursorPage<>(
                List.of(summary("100", 2L, 3000L)), null, false);
        when(userService.listFollowingIds(1L)).thenReturn(followingIds);
        when(feedInboxService.shouldRebuild(1L)).thenReturn(false);
        when(feedInboxService.read(1L))
                .thenThrow(new RedisConnectionFailureException("redis down"));
        when(postService.listByAuthors(followingIds, null, 20)).thenReturn(expected);

        CursorPage<PostSummaryVO> page = feedService.followFeed(null, 20);

        assertThat(page).isSameAs(expected);
        verify(postService).listByAuthors(followingIds, null, 20);
        verify(feedInboxService).markRead(1L);
    }

    @Test
    void shouldFilterByCursorAndOrderSameMillisByPostId() {
        String cursor = CursorCodec.encodeMillis(2000L, 103L);
        when(userService.listFollowingIds(1L)).thenReturn(List.of(2L));
        when(feedInboxService.read(1L)).thenReturn(List.of(
                new InboxEntry(105L, 3000L),
                new InboxEntry(102L, 2000L),
                new InboxEntry(100L, 2000L),
                new InboxEntry(104L, 1000L)));
        when(postService.listSummariesByIds(any())).thenReturn(summaries(
                summary("105", 2L, 3000L),
                summary("102", 2L, 2000L),
                summary("100", 2L, 2000L),
                summary("104", 2L, 1000L)));

        CursorPage<PostSummaryVO> page = feedService.followFeed(cursor, 1);

        assertThat(page.items()).extracting(PostSummaryVO::id).containsExactly("102");
        CursorCodec.CursorValue next = CursorCodec.decode(page.nextCursor());
        assertThat(next.id()).isEqualTo(102L);
        assertThat(next.epochMilli()).isEqualTo(2000L);
        // 非首页不触发重建。
        verify(feedInboxService, never()).shouldRebuild(1L);
        verify(feedInboxService, never()).rebuild(any(), any());
    }

    @Test
    void shouldFilterUnfollowedAuthorsFromInboxAndFallback() {
        List<Long> followingIds = List.of(2L);
        CursorPage<PostSummaryVO> expected = new CursorPage<>(
                List.of(summary("100", 2L, 3000L)), null, false);
        when(userService.listFollowingIds(1L)).thenReturn(followingIds);
        when(feedInboxService.shouldRebuild(1L)).thenReturn(false);
        when(feedInboxService.read(1L)).thenReturn(List.of(
                new InboxEntry(100L, 3000L),
                new InboxEntry(101L, 2000L),
                new InboxEntry(102L, 1000L)));
        when(postService.listSummariesByIds(any())).thenReturn(summaries(
                summary("100", 2L, 3000L),
                summary("101", 9L, 2000L),
                summary("102", 2L, 1000L)));
        when(postService.listByAuthors(followingIds, null, 2)).thenReturn(expected);

        CursorPage<PostSummaryVO> page = feedService.followFeed(null, 2);

        assertThat(page).isSameAs(expected);
    }

    @Test
    void shouldRebuildInboxOnFirstPageWhenStale() {
        List<Long> followingIds = List.of(2L, 3L);
        CursorPage<PostSummaryVO> rebuildSource = new CursorPage<>(
                List.of(summary("1001", 2L, 5000L), summary("1002", 3L, 4000L)),
                "ignored", false);
        CursorPage<PostSummaryVO> fallback = new CursorPage<>(
                List.of(summary("1001", 2L, 5000L)), null, false);
        when(userService.listFollowingIds(1L)).thenReturn(followingIds);
        when(feedInboxService.shouldRebuild(1L)).thenReturn(true);
        when(postService.listByAuthors(followingIds, null, 50)).thenReturn(rebuildSource);
        when(feedInboxService.read(1L)).thenReturn(List.of());
        when(postService.listByAuthors(followingIds, null, 2)).thenReturn(fallback);

        CursorPage<PostSummaryVO> page = feedService.followFeed(null, 2);

        assertThat(page).isSameAs(fallback);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<InboxPost>> captor = ArgumentCaptor.forClass(List.class);
        verify(feedInboxService).rebuild(eq(1L), captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new InboxPost(1001L, 5000L),
                new InboxPost(1002L, 4000L));
    }

    @Test
    void shouldFilterSelfPostsFromInbox() {
        List<Long> followingIds = List.of(1L, 2L);
        CursorPage<PostSummaryVO> expected = new CursorPage<>(
                List.of(summary("101", 2L, 2000L)), null, false);
        when(userService.listFollowingIds(1L)).thenReturn(followingIds);
        when(feedInboxService.shouldRebuild(1L)).thenReturn(false);
        when(feedInboxService.read(1L)).thenReturn(List.of(
                new InboxEntry(100L, 3000L),
                new InboxEntry(101L, 2000L),
                new InboxEntry(102L, 1000L)));
        when(postService.listSummariesByIds(any())).thenReturn(summaries(
                summary("100", 1L, 3000L),
                summary("101", 2L, 2000L),
                summary("102", 2L, 1000L)));
        when(postService.listByAuthors(followingIds, null, 2)).thenReturn(expected);

        // 过滤自己后只剩 2 条，凑不满 3 条，回源。
        CursorPage<PostSummaryVO> page = feedService.followFeed(null, 2);

        assertThat(page).isSameAs(expected);
        verify(postService).listByAuthors(followingIds, null, 2);
    }

    private PostSummaryVO summary(String id, long authorId, long publishedAt) {
        return new PostSummaryVO(
                id, "周末去山里露营", "https://cdn.example.com/1.jpg",
                new UserBriefVO(String.valueOf(authorId), "作者" + authorId, ""),
                List.of(), 1, 2, 3, 4, publishedAt, false, false);
    }

    private Map<Long, PostSummaryVO> summaries(PostSummaryVO... items) {
        Map<Long, PostSummaryVO> map = new LinkedHashMap<>();
        for (PostSummaryVO item : items) {
            map.put(Long.parseLong(item.id()), item);
        }
        return map;
    }
}
