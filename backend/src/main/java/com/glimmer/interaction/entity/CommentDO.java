package com.glimmer.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_comment：两级评论树，parent_id=0 为一级评论，否则为根评论 ID。
 */
@Data
@TableName("t_comment")
public class CommentDO {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属帖子 ID。 */
    private Long postId;

    /** 评论者用户 ID。 */
    private Long userId;

    /** 0=一级评论；否则为根评论 ID。 */
    private Long parentId;

    /** 实际被回复用户 ID；一级评论为空。 */
    private Long replyToUserId;

    /** 评论内容，纯文本，最长 1000 字符。 */
    private String content;

    /** 0=正常, 1=已删除。 */
    private Integer isDeleted;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
