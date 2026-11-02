package com.glimmer.interaction.service.impl;

import com.glimmer.common.api.CursorPage;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.web.LoginUser;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.interaction.entity.CollectDO;
import com.glimmer.interaction.mapper.CollectMapper;
import com.glimmer.interaction.vo.CollectResultVO;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CollectServiceImplTest {

    private CollectMapper collectMapper;
    private PostService postService;
    private CollectServiceImpl collectService;

    @BeforeEach
    void setUp() {
        collectMapper = mock(CollectMapper.class);
        postService = mock(PostService.class);
        collectService = new CollectServiceImpl(collectMapper, postService);
        UserContext.set(new LoginUser(1L, "USER"));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldCollectPostAndIncreaseCount() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(collectMapper.insertIgnore(1L, 1001L)).thenReturn(1);
        when(postService.onCollectChanged(1001L, 1)).thenReturn(5);

        CollectResultVO response = collectService.collect("1001");

        assertThat(response.postId()).isEqualTo("1001");
        assertThat(response.collected()).isTrue();
        assertThat(response.collectCount()).isEqualTo(5);
    }

    @Test
    void shouldTreatRepeatCollectAsIdempotent() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(collectMapper.insertIgnore(1L, 1001L)).thenReturn(0);
        when(postService.onCollectChanged(1001L, 0)).thenReturn(4);

        CollectResultVO response = collectService.collect("1001");

        assertThat(response.collected()).isTrue();
        assertThat(response.collectCount()).isEqualTo(4);
    }

    @Test
    void shouldUncollectPostAndDecreaseCount() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(collectMapper.delete(any())).thenReturn(1);
        when(postService.onCollectChanged(1001L, -1)).thenReturn(3);

        CollectResultVO response = collectService.uncollect("1001");

        assertThat(response.collected()).isFalse();
        assertThat(response.collectCount()).isEqualTo(3);
    }

    @Test
    void shouldTreatRepeatUncollectAsIdempotent() {
        when(postService.resolvePublicPostId("1001")).thenReturn(1001L);
        when(collectMapper.delete(any())).thenReturn(0);
        when(postService.onCollectChanged(1001L, 0)).thenReturn(4);

        CollectResultVO response = collectService.uncollect("1001");

        assertThat(response.collected()).isFalse();
        assertThat(response.collectCount()).isEqualTo(4);
    }

    @Test
    void shouldRejectCollectForInvisiblePost() {
        when(postService.resolvePublicPostId("1001"))
                .thenThrow(new BizException(ErrorCode.POST_NOT_VISIBLE));

        assertThatThrownBy(() -> collectService.collect("1001"))
                .isInstanceOfSatisfying(BizException.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.POST_NOT_VISIBLE));
        verifyNoInteractions(collectMapper);
    }

    @Test
    void shouldPageMyCollectionsInRelationOrder() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 29, 12, 0);
        CollectDO newest = collect(1L, 1001L, now);
        CollectDO older = collect(2L, 1002L, now.minusMinutes(1));
        when(collectMapper.selectList(any()))
                .thenReturn(List.of(newest, older))
                .thenReturn(List.of(older));
        when(postService.listSummariesByIds(List.of(1001L)))
                .thenReturn(Map.of(1001L, summary(1001L)));
        when(postService.listSummariesByIds(List.of(1002L)))
                .thenReturn(Map.of(1002L, summary(1002L)));

        CursorPage<PostSummaryVO> firstPage = collectService.listMyCollections(null, 1);

        assertThat(firstPage.items()).extracting(PostSummaryVO::id).containsExactly("1001");
        assertThat(firstPage.hasMore()).isTrue();
        assertThat(firstPage.nextCursor()).isNotBlank();

        CursorPage<PostSummaryVO> secondPage = collectService.listMyCollections(
                firstPage.nextCursor(), 1);

        assertThat(secondPage.items()).extracting(PostSummaryVO::id).containsExactly("1002");
        assertThat(secondPage.hasMore()).isFalse();
        assertThat(secondPage.nextCursor()).isNull();
    }

    @Test
    void shouldSkipHiddenPostsWhenFillingSummaries() {
        CollectDO row = collect(1L, 1001L, LocalDateTime.of(2026, 10, 29, 12, 0));
        when(collectMapper.selectList(any())).thenReturn(List.of(row));
        when(postService.listSummariesByIds(List.of(1001L))).thenReturn(Map.of());

        CursorPage<PostSummaryVO> page = collectService.listMyCollections(null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.hasMore()).isFalse();
    }

    @Test
    void shouldReturnEmptyPageWhenNoCollections() {
        when(collectMapper.selectList(any())).thenReturn(List.of());

        CursorPage<PostSummaryVO> page = collectService.listMyCollections(null, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.nextCursor()).isNull();
        assertThat(page.hasMore()).isFalse();
        verify(postService, never()).listSummariesByIds(any());
    }

    private CollectDO collect(long id, long postId, LocalDateTime createdAt) {
        CollectDO collect = new CollectDO();
        collect.setId(id);
        collect.setUserId(1L);
        collect.setPostId(postId);
        collect.setCreatedAt(createdAt);
        return collect;
    }

    private PostSummaryVO summary(long postId) {
        return new PostSummaryVO(
                String.valueOf(postId), "标题", "", new UserBriefVO("1", "小光", ""),
                List.of(), 0, 0, 0, 0, 0L, false, true);
    }
}
