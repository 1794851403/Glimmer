package com.glimmer.interaction.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.glimmer.content.service.InteractionStatusProvider;
import com.glimmer.interaction.entity.CollectDO;
import com.glimmer.interaction.entity.LikeDO;
import com.glimmer.interaction.mapper.CollectMapper;
import com.glimmer.interaction.mapper.LikeMapper;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 互动状态只读端口的实现。
 *
 * <p>只查 t_like / t_collect，不注入 LikeService / CollectService，
 * 避免 content → 本实现 → 互动 Service → content 的 Bean 循环。
 */
@Service
public class InteractionStatusProviderImpl implements InteractionStatusProvider {

    private final LikeMapper likeMapper;
    private final CollectMapper collectMapper;

    public InteractionStatusProviderImpl(LikeMapper likeMapper, CollectMapper collectMapper) {
        this.likeMapper = likeMapper;
        this.collectMapper = collectMapper;
    }

    /**
     * 批量查询当前用户点过赞的帖子 ID。
     *
     * @param userId  当前登录用户 ID
     * @param postIds 当前页帖子 ID 集合
     * @return 点过赞的帖子 ID 集合
     */
    @Override
    public Set<Long> likedPostIds(Long userId, Collection<Long> postIds) {
        List<Long> ids = validPostIds(postIds);
        if (userId == null || ids.isEmpty()) {
            return Set.of();
        }
        List<LikeDO> rows = likeMapper.selectList(new LambdaQueryWrapper<LikeDO>()
                .select(LikeDO::getPostId)
                .eq(LikeDO::getUserId, userId)
                .in(LikeDO::getPostId, ids));
        if (rows == null) {
            return Set.of();
        }
        return rows.stream()
                .map(LikeDO::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * 批量查询当前用户收藏过的帖子 ID。
     *
     * @param userId  当前登录用户 ID
     * @param postIds 当前页帖子 ID 集合
     * @return 收藏过的帖子 ID 集合
     */
    @Override
    public Set<Long> collectedPostIds(Long userId, Collection<Long> postIds) {
        List<Long> ids = validPostIds(postIds);
        if (userId == null || ids.isEmpty()) {
            return Set.of();
        }
        List<CollectDO> rows = collectMapper.selectList(new LambdaQueryWrapper<CollectDO>()
                .select(CollectDO::getPostId)
                .eq(CollectDO::getUserId, userId)
                .in(CollectDO::getPostId, ids));
        if (rows == null) {
            return Set.of();
        }
        return rows.stream()
                .map(CollectDO::getPostId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private List<Long> validPostIds(Collection<Long> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return List.of();
        }
        return postIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
