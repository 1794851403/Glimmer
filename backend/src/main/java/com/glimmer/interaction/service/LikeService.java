package com.glimmer.interaction.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.content.vo.PostSummaryVO;
import com.glimmer.interaction.vo.LikeResultVO;

/**
 * 点赞模块对外的服务：点赞、取消点赞与我的点赞列表。
 */
public interface LikeService {

    /**
     * 点赞帖子，需登录。
     *
     * <p>一人一赞；重复点赞幂等，返回当前状态与最新点赞数。
     * 帖子不存在或不可见时返回 40402。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 当前状态与点赞数
     */
    LikeResultVO like(String postId);

    /**
     * 取消点赞，需登录。
     *
     * <p>重复取消幂等；帖子不存在或不可见时返回 40402。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @return 当前状态与点赞数
     */
    LikeResultVO unlike(String postId);

    /**
     * 我的点赞列表，按点赞时间倒序。
     *
     * <p>只返回当前仍公开可见的帖子；坏游标按首页处理，size 默认 20、越界夹到 1~50。
     *
     * @param cursor 上一页返回的 nextCursor，可为空
     * @param size   每页数量，可为空
     * @return 帖子摘要游标页
     */
    CursorPage<PostSummaryVO> listMyLikes(String cursor, Integer size);
}
