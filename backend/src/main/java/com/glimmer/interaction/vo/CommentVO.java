package com.glimmer.interaction.vo;

import com.glimmer.user.vo.UserBriefVO;

/**
 * 一级评论响应；不包含二级回复内容，只带 replyCount 供前端按需加载。
 */
public record CommentVO(
        /** 评论 ID。 */
        String id,

        /** 所属帖子 ID。 */
        String postId,

        /** 固定为 "0"；一级评论列表里没有父评论。 */
        String parentId,

        /** 评论内容；已删除的根评论固定为“该评论已删除”。 */
        String content,

        /** 评论作者；已删除的根评论为 null。 */
        UserBriefVO author,

        /** 被回复用户；一级评论为 null。 */
        UserBriefVO replyToUser,

        /** 是否为已删除但有回复的占位评论。 */
        boolean deleted,

        /** 该根评论下未删除的二级回复数。 */
        int replyCount,

        /** 创建时间，epoch 毫秒。 */
        long createdAt
) {
}
