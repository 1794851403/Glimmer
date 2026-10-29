package com.glimmer.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_user_follow：关注关系。
 */
@Data
@TableName("t_user_follow")
public class UserFollowDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关注者。 */
    private Long followerId;

    /** 被关注者。 */
    private Long followeeId;

    /** 关注时间。 */
    private LocalDateTime createdAt;
}
