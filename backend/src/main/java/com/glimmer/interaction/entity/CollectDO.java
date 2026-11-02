package com.glimmer.interaction.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_collect：用户与帖子的收藏关系，一人一帖唯一。
 */
@Data
@TableName("t_collect")
public class CollectDO {

    /** 主键，数据库自增。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 收藏用户 ID。 */
    private Long userId;

    /** 被收藏帖子 ID。 */
    private Long postId;

    /** 收藏时间。 */
    private LocalDateTime createdAt;
}
