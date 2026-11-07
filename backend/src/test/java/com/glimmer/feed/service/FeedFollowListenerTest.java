package com.glimmer.feed.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.user.event.UserFollowedEvent;
import com.glimmer.user.vo.UserBriefVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedFollowListenerTest {

    private PostService postService;
    private FeedInboxService feedInboxService;
    private FeedFollowListener listener;

    @BeforeEach
    void setUp() {
        postService = mock(PostService.class);
        feedInboxService = mock(FeedInboxService.class);
        listener = new FeedFollowListener(postService, feedInboxService);
    }

    @Test
    void shouldIgnoreInvalidEvent() {
        listener.onUserFollowed(null);
        listener.onUserFollowed(new UserFollowedEvent(null, 2L));
        listener.onUserFollowed(new UserFollowedEvent(1L, null));

        verifyNoInteractions(postService, feedInboxService);
    }

    @Test
    void shouldBackfillLatestPostsIntoFollowerInbox() {
        when(postService.listByAuthors(List.of(2L), null, 50)).thenReturn(new CursorPage<>(
                List.of(summary("1001", 5000L), summary("1002", 4000L)), "next", true));

        listener.onUserFollowed(new UserFollowedEvent(1L, 2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeedInboxService.InboxPost>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(feedInboxService).addPostsToInbox(eq(1L), captor.capture());
        assertThat(captor.getValue()).containsExactly(
                new FeedInboxService.InboxPost(1001L, 5000L),
                new FeedInboxService.InboxPost(1002L, 4000L));
    }

    @Test
    void shouldSkipInvalidPostIds() {
        when(postService.listByAuthors(List.of(2L), null, 50)).thenReturn(new CursorPage<>(
                List.of(summary("abc", 5000L), summary("1002", 4000L)), null, false));

        listener.onUserFollowed(new UserFollowedEvent(1L, 2L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<FeedInboxService.InboxPost>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(feedInboxService).addPostsToInbox(eq(1L), captor.capture());
        assertThat(captor.getValue())
                .containsExactly(new FeedInboxService.InboxPost(1002L, 4000L));
    }

    @Test
    void shouldNotWriteWhenNoPosts() {
        when(postService.listByAuthors(List.of(2L), null, 50))
                .thenReturn(CursorPage.empty());

        listener.onUserFollowed(new UserFollowedEvent(1L, 2L));

        verify(feedInboxService, never()).addPostsToInbox(anyLong(), any());
    }

    @Test
    void shouldSwallowBackfillFailure() {
        when(postService.listByAuthors(List.of(2L), null, 50))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> listener.onUserFollowed(new UserFollowedEvent(1L, 2L)))
                .doesNotThrowAnyException();
        verify(feedInboxService, never()).addPostsToInbox(anyLong(), any());
    }

    private PostSummaryVO summary(String id, long publishedAt) {
        return new PostSummaryVO(
                id, "标题", "", new UserBriefVO("2", "阿野", ""),
                List.of(), 0, 0, 0, 0, publishedAt, false, false);
    }
}
