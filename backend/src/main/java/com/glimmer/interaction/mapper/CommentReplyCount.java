package com.glimmer.interaction.mapper;

import lombok.Data;

/**
 * 根评论下未删除二级回复数的聚合结果。
 */
@Data
public class CommentReplyCount {

    /** 根评论 ID。 */
    private Long parentId;

    /** 未删除的二级回复数。 */
    private Integer replyCount;
}
