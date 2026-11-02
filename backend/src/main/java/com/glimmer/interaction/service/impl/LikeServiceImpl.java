package com.glimmer.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.common.api.CursorCodec;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.interaction.entity.LikeDO;
import com.glimmer.interaction.mapper.LikeMapper;
import com.glimmer.interaction.service.LikeService;
import com.glimmer.interaction.vo.LikeResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 点赞实现：关系写 t_like，计数更新委托 content，两边在同一事务内提交。
 */
@Service
public class LikeServiceImpl implements LikeService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final LikeMapper likeMapper;
    private final PostService postService;

    public LikeServiceImpl(LikeMapper likeMapper, PostService postService) {
        this.likeMapper = likeMapper;
        this.postService = postService;
    }

    /**
     * 先校验帖子公开可见，再插入点赞关系；只有真正插入成功才让 content 加计数，
     * 重复点赞传 0，只读取当前计数。关系与计数在同一事务内提交。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 当前状态与点赞数
     */
    @Override
    @Transactional
    public LikeResultVO like(String rawPostId) {
        Long userId = UserContext.require().userId();
        Long postId = postService.resolvePublicPostId(rawPostId);

        int affected = likeMapper.insertIgnore(userId, postId);
        int likeCount = postService.onLikeChanged(postId, affected > 0 ? 1 : 0);
        return new LikeResultVO(String.valueOf(postId), true, likeCount);
    }

    /**
     * 删除点赞关系；只有真正删到行才让 content 减计数，重复取消传 0。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 当前状态与点赞数
     */
    @Override
    @Transactional
    public LikeResultVO unlike(String rawPostId) {
        Long userId = UserContext.require().userId();
        Long postId = postService.resolvePublicPostId(rawPostId);

        int affected = likeMapper.delete(new LambdaQueryWrapper<LikeDO>()
                .eq(LikeDO::getUserId, userId)
                .eq(LikeDO::getPostId, postId));
        int likeCount = postService.onLikeChanged(postId, affected > 0 ? -1 : 0);
        return new LikeResultVO(String.valueOf(postId), false, likeCount);
    }

    /**
     * 按点赞关系自身的 created_at / id 倒序游标分页；帖子摘要由 content 批量回填，
     * 已删除或不可见的帖子跳过，但游标仍取最后一条关系记录，保证分页继续向前。
     *
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量，可为空
     * @return 帖子摘要游标页
     */
    @Override
    public CursorPage<PostSummaryVO> listMyLikes(String rawCursor, Integer rawSize) {
        Long userId = UserContext.require().userId();
        int size = rawSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(rawSize, MAX_PAGE_SIZE));
        CursorCodec.CursorValue cursor = CursorCodec.decode(rawCursor);

        LambdaQueryWrapper<LikeDO> query = new LambdaQueryWrapper<LikeDO>()
                .eq(LikeDO::getUserId, userId);
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .lt(LikeDO::getCreatedAt, cursor.time())
                    .or(sameTime -> sameTime
                            .eq(LikeDO::getCreatedAt, cursor.time())
                            .lt(LikeDO::getId, cursor.id())));
        }
        query.orderByDesc(LikeDO::getCreatedAt)
                .orderByDesc(LikeDO::getId)
                .last("LIMIT " + (size + 1));

        List<LikeDO> rows = safeList(likeMapper.selectList(query));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        List<Long> postIds = rows.stream()
                .map(LikeDO::getPostId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, PostSummaryVO> summaries = postService.listSummariesByIds(postIds);
        if (summaries == null) {
            summaries = Map.of();
        }

        List<PostSummaryVO> items = new ArrayList<>(rows.size());
        for (LikeDO row : rows) {
            PostSummaryVO summary = summaries.get(row.getPostId());
            if (summary != null) {
                items.add(summary);
            }
        }

        String nextCursor = null;
        if (hasMore) {
            LikeDO last = rows.get(rows.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
