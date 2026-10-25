package com.glimmer.user.dto;

import com.glimmer.user.entity.UserDO;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 登录响应中的用户信息。ID 按接口约定序列化为字符串；时间用 epoch 毫秒。
 */
public record UserProfile(
        /** 用户 ID，以字符串序列化，避免前端 Long 精度丢失。 */
        String id,

        /** 手机号，已脱敏（如 138****8000）。 */
        String phone,

        /** 昵称。 */
        String nickname,

        /** 头像 URL；空字符串表示未设置。 */
        String avatarUrl,

        /** 个人简介；空字符串表示未填写。 */
        String bio,

        /** 角色：{@code USER} 普通用户 / {@code ADMIN} 管理员（由数据库 role 0/1 映射）。 */
        String role,

        /** 账号状态：0 正常，1 封禁。 */
        int status,

        /** 关注数：该用户关注了多少人。 */
        int followingCount,

        /** 粉丝数：多少人关注了该用户。 */
        int followerCount,

        /** 注册时间，epoch 毫秒（按 Asia/Shanghai 时区换算）。 */
        long createdAt
) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static UserProfile from(UserDO user) {
        return new UserProfile(
                String.valueOf(user.getId()),
                maskPhone(user.getPhone()),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getBio(),
                user.getRole() != null && user.getRole() == 1 ? "ADMIN" : "USER",
                value(user.getStatus()),
                value(user.getFollowingCount()),
                value(user.getFollowerCount()),
                epochMillis(user.getCreatedAt())
        );
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZONE).toInstant().toEpochMilli();
    }

    private static String maskPhone(String phone) {
        if (phone == null || phone.length() != 11) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
