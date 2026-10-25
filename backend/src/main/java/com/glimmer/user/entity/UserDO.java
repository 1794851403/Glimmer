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

    /** 主键，数据库自增；对外接口统一序列化为字符串，避免前端 Long 精度丢失。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 手机号，登录唯一标识（唯一索引 uk_phone）。 */
    private String phone;

    /** 昵称，允许重复。 */
    private String nickname;

    /** 头像 URL；未设置时为空字符串。 */
    private String avatarUrl;

    /** 个人简介；未填写时为空字符串。 */
    private String bio;

    /** 角色：0 普通用户，1 管理员（数据库 CHECK 约束只允许这两个值）。 */
    private Integer role;

    /** 账号状态：0 正常，1 封禁；封禁时同时清理 Redis Token 白名单让用户下线。 */
    private Integer status;

    /** 关注数：该用户关注了多少人；由关注事务维护，不用 COUNT(*) 实时统计。 */
    private Integer followingCount;

    /** 粉丝数：多少人关注了该用户；同样由关注事务维护。 */
    private Integer followerCount;

    /** 创建时间，写入后不再变更。 */
    private LocalDateTime createdAt;

    /** 更新时间，内容 / 状态变更时刷新；纯计数变化不更新它。 */
    private LocalDateTime updatedAt;
}
