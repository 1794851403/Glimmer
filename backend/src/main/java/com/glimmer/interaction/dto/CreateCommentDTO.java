package com.glimmer.interaction.dto;

/**
 * 发表评论 / 回复入参。
 *
 * <p>ID 使用字符串，避免前端 Long 精度丢失；parentId 为 "0" 或省略表示一级评论。
 */
public record CreateCommentDTO(
        /** 评论内容，去首尾空白后 1~1000 字符。 */
        String content,

        /** 父评论 ID；一级评论可省略或传 "0"。 */
        String parentId,

        /** 被回复用户 ID；回复根评论时可省略，回复某条回复时必须显式传入。 */
        String replyToUserId
) {
}
