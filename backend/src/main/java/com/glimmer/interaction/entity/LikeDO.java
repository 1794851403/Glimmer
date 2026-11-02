package com.glimmer.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_like：用户与帖子的点赞关系，一人一帖唯一。
 */
@Data
@TableName("t_like")
public class LikeDO {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 点赞用户 ID。 */
    private Long userId;

    /** 被点赞帖子 ID。 */
    private Long postId;

    /** 点赞时间。 */
    private LocalDateTime createdAt;
}
