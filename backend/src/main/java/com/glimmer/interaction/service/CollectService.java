package com.glimmer.interaction.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.interaction.vo.CollectResultVO;

/**
 * 收藏模块对外的服务：收藏、取消收藏与我的收藏列表。
 */
public interface CollectService {

    /**
     * 收藏帖子，需登录。
     *
     * <p>一人一收藏；重复收藏幂等，返回当前状态与最新收藏数。
     * 帖子不存在或不可见时返回 40402。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 当前状态与收藏数
     */
    CollectResultVO collect(String postId);

    /**
     * 取消收藏，需登录。
     *
     * <p>重复取消幂等；帖子不存在或不可见时返回 40402。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 当前状态与收藏数
     */
    CollectResultVO uncollect(String postId);

    /**
     * 我的收藏列表，按收藏时间倒序。
     *
     * <p>只返回当前仍公开可见的帖子；坏游标按首页处理，size 默认 20、越界夹到 1~50。
     *
     * @param cursor 上一页返回的 nextCursor，可为空
     * @param size   每页数量，可为空
     * @return 帖子摘要游标页
     */
    CursorPage<PostSummaryVO> listMyCollections(String cursor, Integer size);
}
