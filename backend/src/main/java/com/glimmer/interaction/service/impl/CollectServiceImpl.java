package com.glimmer.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.common.api.CursorCodec;
import com.glimmer.common.api.CursorPage;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.service.PostService;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.interaction.entity.CollectDO;
import com.glimmer.interaction.mapper.CollectMapper;
import com.glimmer.interaction.service.CollectService;
import com.glimmer.interaction.vo.CollectResultVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 收藏实现：关系写 t_collect，计数更新委托 content，两边在同一事务内提交。
 */
@Service
public class CollectServiceImpl implements CollectService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 50;

    private final CollectMapper collectMapper;
    private final PostService postService;

    public CollectServiceImpl(CollectMapper collectMapper, PostService postService) {
        this.collectMapper = collectMapper;
        this.postService = postService;
    }

    /**
     * 先校验帖子公开可见，再插入收藏关系；只有真正插入成功才让 content 加计数，
     * 重复收藏传 0，只读取当前计数。关系与计数在同一事务内提交。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 当前状态与收藏数
     */
    @Override
    @Transactional
    public CollectResultVO collect(String rawPostId) {
        Long userId = UserContext.require().userId();
        Long postId = postService.resolvePublicPostId(rawPostId);

        int affected = collectMapper.insertIgnore(userId, postId);
        int collectCount = postService.onCollectChanged(postId, affected > 0 ? 1 : 0);
        return new CollectResultVO(String.valueOf(postId), true, collectCount);
    }

    /**
     * 删除收藏关系；只有真正删到行才让 content 减计数，重复取消传 0。
     *
     * @param rawPostId 帖子 ID，允许数字字符串
     * @return 当前状态与收藏数
     */
    @Override
    @Transactional
    public CollectResultVO uncollect(String rawPostId) {
        Long userId = UserContext.require().userId();
        Long postId = postService.resolvePublicPostId(rawPostId);

        int affected = collectMapper.delete(new LambdaQueryWrapper<CollectDO>()
                .eq(CollectDO::getUserId, userId)
                .eq(CollectDO::getPostId, postId));
        int collectCount = postService.onCollectChanged(postId, affected > 0 ? -1 : 0);
        return new CollectResultVO(String.valueOf(postId), false, collectCount);
    }

    /**
     * 按收藏关系自身的 created_at / id 倒序游标分页；帖子摘要由 content 批量回填，
     * 已删除或不可见的帖子跳过，但游标仍取最后一条关系记录，保证分页继续向前。
     *
     * @param rawCursor 上一页游标，可为空
     * @param rawSize   每页数量，可为空
     * @return 帖子摘要游标页
     */
    @Override
    public CursorPage<PostSummaryVO> listMyCollections(String rawCursor, Integer rawSize) {
        Long userId = UserContext.require().userId();
        int size = rawSize == null
                ? DEFAULT_PAGE_SIZE
                : Math.max(1, Math.min(rawSize, MAX_PAGE_SIZE));
        CursorCodec.CursorValue cursor = CursorCodec.decode(rawCursor);

        LambdaQueryWrapper<CollectDO> query = new LambdaQueryWrapper<CollectDO>()
                .eq(CollectDO::getUserId, userId);
        if (cursor != null) {
            query.and(wrapper -> wrapper
                    .lt(CollectDO::getCreatedAt, cursor.time())
                    .or(sameTime -> sameTime
                            .eq(CollectDO::getCreatedAt, cursor.time())
                            .lt(CollectDO::getId, cursor.id())));
        }
        query.orderByDesc(CollectDO::getCreatedAt)
                .orderByDesc(CollectDO::getId)
                .last("LIMIT " + (size + 1));

        List<CollectDO> rows = safeList(collectMapper.selectList(query));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, size));
        }
        if (rows.isEmpty()) {
            return CursorPage.empty();
        }

        List<Long> postIds = rows.stream()
                .map(CollectDO::getPostId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, PostSummaryVO> summaries = postService.listSummariesByIds(postIds);
        if (summaries == null) {
            summaries = Map.of();
        }

        List<PostSummaryVO> items = new ArrayList<>(rows.size());
        for (CollectDO row : rows) {
            PostSummaryVO summary = summaries.get(row.getPostId());
            if (summary != null) {
                items.add(summary);
            }
        }

        String nextCursor = null;
        if (hasMore) {
            CollectDO last = rows.get(rows.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt(), last.getId());
        }
        return new CursorPage<>(items, nextCursor, hasMore);
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
