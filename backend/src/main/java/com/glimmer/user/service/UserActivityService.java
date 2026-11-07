package com.glimmer.user.service;

import java.util.Collection;
import java.util.Set;

/**
 * 用户活跃状态对 Feed 的只读 / 标记 Facade。
 *
 * <p>底层是 cache Redis 的 {@code glimmer:user:active} ZSet，属于可丢缓存：
 * 读取失败或集合为空时调用方按“全部活跃”处理，避免因为缓存故障停止写扩散。
 */
public interface UserActivityService {

    /**
     * 标记用户活跃；失败只记日志，不影响调用方主流程。
     *
     * @param userId 用户 ID
     */
    void markActive(Long userId);

    /**
     * 从候选用户中筛出最近活跃用户，供 Feed 写扩散裁剪。
     *
     * <p>Redis 异常或活跃集合整体为空时返回全部候选用户（fail-open），
     * 宁可多写，也不要因为缓存故障漏推。
     *
     * @param userIds 候选用户 ID 集合，允许为空或重复
     * @return 活跃用户 ID 集合；没有候选时返回空集合
     */
    Set<Long> filterActiveUserIds(Collection<Long> userIds);
}
