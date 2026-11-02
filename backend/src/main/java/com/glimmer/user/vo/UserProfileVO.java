package com.glimmer.user.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.glimmer.user.entity.UserDO;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 用户公开主页出参。
 *
 * <p>只返回公开资料，不包含手机号、角色与封禁状态。
 */
public record UserProfileVO(
        /** 用户 ID，以字符串序列化。 */
        String id,

        /** 昵称。 */
        String nickname,

        /** 头像 URL；未设置时为空字符串。 */
        String avatarUrl,

        /** 个人简介；未填写时为空字符串。 */
        String bio,

        /** 关注数。 */
        int followingCount,

        /** 粉丝数。 */
        int followerCount,

        /** 当前登录用户是否已关注该用户；匿名访问时为 false。 */
        @JsonProperty("isFollowing") boolean isFollowing,

        /** 当前访问者是否就是该用户；匿名访问时为 false。 */
        @JsonProperty("isMe") boolean isMe,

        /** 注册时间，epoch 毫秒。 */
        long createdAt
) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static UserProfileVO from(UserDO user, boolean isFollowing, boolean isMe) {
        return new UserProfileVO(
                String.valueOf(user.getId()),
                user.getNickname(),
                user.getAvatarUrl() == null ? "" : user.getAvatarUrl(),
                user.getBio() == null ? "" : user.getBio(),
                value(user.getFollowingCount()),
                value(user.getFollowerCount()),
                isFollowing,
                isMe,
                epochMillis(user.getCreatedAt())
        );
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
