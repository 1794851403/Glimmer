package com.glimmer.feed.service.impl;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.user.service.UserService;
import com.glimmer.user.vo.UserBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FeedServiceImplTest {

    private UserService userService;
    private PostService postService;
    private FeedServiceImpl feedService;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        postService = mock(PostService.class);
        feedService = new FeedServiceImpl(userService, postService);
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
        verifyNoInteractions(postService);
    }

    @Test
    void shouldDelegateToPostsOfFollowedAuthors() {
        List<Long> followingIds = List.of(2L, 3L);
        CursorPage<PostSummaryVO> expected = new CursorPage<>(
                List.of(summary("1001", "小光")), "next-cursor", true);
        when(userService.listFollowingIds(1L)).thenReturn(followingIds);
        when(postService.listByAuthors(followingIds, "cursor", 10)).thenReturn(expected);

        CursorPage<PostSummaryVO> page = feedService.followFeed("cursor", 10);

        assertThat(page).isSameAs(expected);
        verify(postService).listByAuthors(followingIds, "cursor", 10);
    }

    private PostSummaryVO summary(String id, String nickname) {
        return new PostSummaryVO(
                id, "周末去山里露营", "https://cdn.example.com/1.jpg",
                new UserBriefVO("2", nickname, ""), List.of(),
                1, 2, 3, 4, 1761700800000L, false, false);
    }
}
