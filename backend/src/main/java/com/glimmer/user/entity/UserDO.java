package com.glimmer.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_user：账号与身份主表。
 */
@Data
@TableName("t_user")
public class UserDO {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String phone;
    private String nickname;
    private String avatarUrl;
    private String bio;
    private Integer role;
    private Integer status;
    private Integer followingCount;
    private Integer followerCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
