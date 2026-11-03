package com.glimmer.interaction.vo;

import com.glimmer.user.vo.UserBriefVO;

/**
 * 二级回复响应；只在用户展开回复时由回复列表接口返回。
 */
public record ReplyVO(
        /** 回复 ID。 */
        String id,

        /** 所属根评论 ID。 */
        String commentId,

        /** 回复内容。 */
        String content,

        /** 回复作者。 */
        UserBriefVO author,

        /** 实际被回复用户。 */
        UserBriefVO replyToUser,

        /** 创建时间，epoch 毫秒。 */
        long createdAt
) {
}
