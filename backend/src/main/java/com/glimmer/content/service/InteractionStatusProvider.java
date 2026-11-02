package com.glimmer.content.service;

import java.util.Collection;
import java.util.Set;

/**
 * 互动状态的只读端口：由 content 定义，interaction 实现。
 *
 * <p>content 需要给帖子详情 / 列表补当前用户的 {@code liked} / {@code collected}，
 * 但不能反向依赖 interaction；因此把“批量查询互动状态”定义成 content 自己拥有的接口，
 * interaction 在运行时提供实现。实现类只查 {@code t_like} / {@code t_collect}，
 * 不调用 {@code LikeService} / {@code CollectService}，避免 Bean 循环依赖。
 */
public interface InteractionStatusProvider {

    /**
     * 批量查询用户点过赞的帖子 ID。
     *
     * @param userId  当前登录用户 ID
     * @param postIds 当前页帖子 ID 集合
     * @return 点过赞的帖子 ID 集合；参数为空时返回空集合
     */
    Set<Long> likedPostIds(Long userId, Collection<Long> postIds);

    /**
     * 批量查询用户收藏过的帖子 ID。
     *
     * @param userId  当前登录用户 ID
     * @param postIds 当前页帖子 ID 集合
     * @return 收藏过的帖子 ID 集合；参数为空时返回空集合
     */
    Set<Long> collectedPostIds(Long userId, Collection<Long> postIds);
}
