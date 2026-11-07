package com.glimmer.feed.service.impl;

import com.glimmer.feed.config.FeedProperties;
import com.glimmer.feed.mq.PostPublishedMessage;
import com.glimmer.feed.service.FeedInboxService;
import com.glimmer.user.service.UserActivityService;
import com.glimmer.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedFanoutServiceImplTest {

    private UserService userService;
    private UserActivityService userActivityService;
    private FeedInboxService feedInboxService;
    private FeedFanoutServiceImpl fanoutService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        userActivityService = mock(UserActivityService.class);
        feedInboxService = mock(FeedInboxService.class);
        fanoutService = new FeedFanoutServiceImpl(
                userService, userActivityService, feedInboxService,
                new FeedProperties(200, 30, 2, 24));
    }

    @Test
    void shouldSkipInvalidMessages() {
        fanoutService.fanout(null);
        fanoutService.fanout(new PostPublishedMessage(null, 2L, 1000L, 1));
        fanoutService.fanout(new PostPublishedMessage(1L, null, 1000L, 1));
        fanoutService.fanout(new PostPublishedMessage(1L, 2L, 0L, 1));
        fanoutService.fanout(new PostPublishedMessage(1L, 2L, 1000L, 2));

        verifyNoInteractions(userService, userActivityService, feedInboxService);
    }

    @Test
    void shouldStopWhenNoFollowers() {
        when(userService.listFollowerIds(2L, 0L, 2)).thenReturn(List.of());

        fanoutService.fanout(new PostPublishedMessage(100L, 2L, 5000L, 1));

        verifyNoInteractions(userActivityService, feedInboxService);
    }

    @Test
    void shouldPageFollowersAndOnlyWriteActiveUsers() {
        when(userService.listFollowerIds(2L, 0L, 2)).thenReturn(List.of(3L, 4L));
        when(userService.listFollowerIds(2L, 4L, 2)).thenReturn(List.of(5L));
        when(userActivityService.filterActiveUserIds(List.of(3L, 4L)))
                .thenReturn(new LinkedHashSet<>(Set.of(3L)));
        when(userActivityService.filterActiveUserIds(List.of(5L)))
                .thenReturn(new LinkedHashSet<>(Set.of(5L)));

        fanoutService.fanout(new PostPublishedMessage(100L, 2L, 5000L, 0));

        verify(feedInboxService).addPostToInboxes(Set.of(3L), 100L, 5000L);
        verify(feedInboxService).addPostToInboxes(Set.of(5L), 100L, 5000L);
    }

    @Test
    void shouldSkipInboxWriteWhenNoActiveFollowers() {
        when(userService.listFollowerIds(2L, 0L, 2)).thenReturn(List.of(3L));
        when(userActivityService.filterActiveUserIds(List.of(3L))).thenReturn(Set.of());

        fanoutService.fanout(new PostPublishedMessage(100L, 2L, 5000L, 1));

        verify(feedInboxService, never())
                .addPostToInboxes(anyCollection(), anyLong(), anyLong());
    }

    @Test
    void shouldStopWhenFollowerCursorDoesNotMove() {
        when(userService.listFollowerIds(2L, 0L, 2)).thenReturn(List.of(3L, 4L));
        when(userService.listFollowerIds(2L, 4L, 2)).thenReturn(List.of(4L, 5L));
        when(userService.listFollowerIds(2L, 5L, 2)).thenReturn(List.of(5L, 5L));
        when(userActivityService.filterActiveUserIds(any()))
                .thenAnswer(invocation -> new LinkedHashSet<>(invocation.getArgument(0)));

        fanoutService.fanout(new PostPublishedMessage(100L, 2L, 5000L, 1));

        verify(userService, times(3)).listFollowerIds(anyLong(), anyLong(), any(Integer.class));
    }
}
