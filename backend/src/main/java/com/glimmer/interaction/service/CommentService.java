package com.glimmer.interaction.service;

import com.glimmer.common.api.CursorPage;
import com.glimmer.interaction.dto.CreateCommentDTO;
import com.glimmer.interaction.vo.CommentVO;
import com.glimmer.interaction.vo.ReplyVO;

/**
 * 评论模块对外的服务：发表、一级列表、回复列表与删除。
 */
public interface CommentService {

    /**
     * 发表一级评论或回复，需登录。
     *
     * <p>只做两级：回复某条回复时把 parentId 归一化为根评论 ID，replyToUserId 记录实际被回复人。
     * 评论写入与帖子评论计数在同一事务内提交；帖子不可见返回 40402。
     *
     * @param postId  帖子 ID，允许数字字符串
     * @param request 评论内容、父评论与被回复用户
     * @return 新评论
     */
    CommentVO create(String postId, CreateCommentDTO request);

    /**
     * 一级评论列表，可选登录。
     *
     * <p>只返回根评论与 replyCount，不返回二级回复内容；已删除但有回复的根评论返回占位。
     * 按创建时间倒序，坏游标按首页处理，size 默认 20、越界夹到 1~50。
     *
     * @param postId 帖子 ID，允许数字字符串
     * @param cursor 上一页返回的 nextCursor，可为空
     * @param size   每页数量，可为空
     * @return 一级评论游标页
     */
    CursorPage<CommentVO> listByPost(String postId, String cursor, Integer size);

    /**
     * 根评论下的二级回复列表，可选登录。
     *
     * <p>commentId 必须是根评论；按创建时间正序，已删除的回复不返回。
     *
     * @param commentId 根评论 ID，允许数字字符串
     * @param cursor    上一页返回的 nextCursor，可为空
     * @param size      每页数量，可为空
     * @return 回复游标页
     */
    CursorPage<ReplyVO> listReplies(String commentId, String cursor, Integer size);

    /**
     * 删除评论 / 回复，需登录。
     *
     * <p>允许评论作者、帖子作者与管理员；逻辑删除，重复删除幂等。
     *
     * @param commentId 评论 ID，允许数字字符串
     */
    void delete(String commentId);
}
