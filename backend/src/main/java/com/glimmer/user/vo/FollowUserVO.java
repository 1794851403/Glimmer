package com.glimmer.user.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 粉丝 / 关注列表条目：用户简要信息，并标明当前登录用户是否已关注该用户。
 */
public record FollowUserVO(
        /** 用户 ID，以字符串序列化。 */
        String id,

        /** 昵称。 */
        String nickname,

        /** 头像 URL；未设置时为空字符串。 */
        String avatarUrl,

        /** 当前登录用户是否已关注该用户；匿名访问时为 false。 */
        @JsonProperty("isFollowing") boolean isFollowing
) {

    public static FollowUserVO from(UserBriefVO brief, boolean isFollowing) {
        return new FollowUserVO(brief.id(), brief.nickname(), brief.avatarUrl(), isFollowing);
    }
}
