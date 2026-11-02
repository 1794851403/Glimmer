package com.glimmer.interaction.service.impl;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.interaction.entity.LikeDO;
import com.glimmer.interaction.mapper.LikeMapper;
import com.glimmer.interaction.vo.LikeResultVO;
import com.glimmer.user.vo.UserBriefVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LikeServiceImplTest {

    private LikeMapper likeMapper;
    private PostService postService;
    private LikeServiceImpl likeService;

    @BeforeEach
    void setUp() {
        likeMapper = mock(LikeMapper.class);
        postService = mock(PostService.class);
        likeService = new LikeServiceImpl(likeMapper, postService);
        UserContext.set(new LoginUser(1L, "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldLikePostAndIncreaseCount() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(likeMapper.insertIgnore(1L, 1001L)).thenReturn(1);
        when(postService.onLikeChanged(1001L, 1)).thenReturn(13);

        LikeResultVO response = likeService.like("1001");

        assertThat(response.postId()).isEqualTo("1001");
        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(13);
    }

    @Test
    void shouldTreatRepeatLikeAsIdempotent() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(likeMapper.insertIgnore(1L, 1001L)).thenReturn(0);
        when(postService.onLikeChanged(1001L, 0)).thenReturn(12);

        LikeResultVO response = likeService.like("1001");

        assertThat(response.liked()).isTrue();
        assertThat(response.likeCount()).isEqualTo(12);
    }

    @Test
    void shouldUnlikePostAndDecreaseCount() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(likeMapper.delete(any())).thenReturn(1);
        when(postService.onLikeChanged(1001L, -1)).thenReturn(11);

        LikeResultVO response = likeService.unlike("1001");

        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(11);
    }

    @Test
    void shouldTreatRepeatUnlikeAsIdempotent() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(likeMapper.delete(any())).thenReturn(0);
        when(postService.onLikeChanged(1001L, 0)).thenReturn(12);

        LikeResultVO response = likeService.unlike("1001");

        assertThat(response.liked()).isFalse();
        assertThat(response.likeCount()).isEqualTo(12);
    }

    @Test
    void shouldRejectLikeForInvisiblePost() {
        when(postService.resolvePublicPostId("1001"))
                .thenThrow(new BizException(ErrorCode.POST_NOT_VISIBLE));

        assertThatThrownBy(() -> likeService.like("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
        verifyNoInteractions(likeMapper);
    }

    @Test
    void shouldPageMyLikesInRelationOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        LikeDO newest = like(1L, 1001L, now);
        LikeDO older = like(2L, 1002L, now.minusMinutes(1));
        when(likeMapper.selectList(any()))
                .thenReturn(List.of(newest, older))
                .thenReturn(List.of(older));
        when(postService.listSummariesByIds(List.of(1001L)))
                .thenReturn(Map.of(1001L, summary(1001L)));
        when(postService.listSummariesByIds(List.of(1002L)))
                .thenReturn(Map.of(1002L, summary(1002L)));

        CursorPage<PostSummaryVO> firstPage = likeService.listMyLikes(null, 1);

        assertThat(firstPage.items()).extracting(PostSummaryVO::id).containsExactly("1001");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        CursorPage<PostSummaryVO> secondPage = likeService.listMyLikes(firstPage.nextCursor(), 1);

        assertThat(secondPage.items()).extracting(PostSummaryVO::id).containsExactly("1002");
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void shouldSkipHiddenPostsWhenFillingSummaries() {
        LikeDO row = like(1L, 1001L, LocalDateTime.of(2026, 10, 29, 12, 0));
        when(likeMapper.selectList(any())).thenReturn(List.of(row));
        when(postService.listSummariesByIds(List.of(1001L))).thenReturn(Map.of());

        CursorPage<PostSummaryVO> page = likeService.listMyLikes(null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void shouldReturnEmptyPageWhenNoLikes() {
        when(likeMapper.selectList(any())).thenReturn(List.of());

        CursorPage<PostSummaryVO> page = likeService.listMyLikes(null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.hasMore()).isFalse();
        verify(postService, never()).listSummariesByIds(any());
    }

    @Test
    void shouldClampPageSize() {
        when(likeMapper.selectList(any())).thenReturn(List.of());

        likeService.listMyLikes("bad-cursor", 999);
        likeService.listMyLikes(null, -1);

        verify(likeMapper, org.mockito.Mockito.times(2)).selectList(any());
    }

    private LikeDO like(long id, long postId, LocalDateTime createdAt) {
        LikeDO like = new LikeDO();
        like.setId(id);
        like.setUserId(1L);
        like.setPostId(postId);
        like.setCreatedAt(createdAt);
        return like;
    }

    private PostSummaryVO summary(long postId) {
        return new PostSummaryVO(
                String.valueOf(postId), "标题", "", new UserBriefVO("1", "小光", ""),
                List.of(), 0, 0, 0, 0, 0L, true, false);
    }
}
