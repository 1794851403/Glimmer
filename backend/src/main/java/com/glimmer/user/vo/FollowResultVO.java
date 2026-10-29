package com.glimmer.user.vo;

/**
 * 关注 / 取关响应。
 */
public record FollowResultVO(
        /** 目标用户 ID，以字符串序列化。 */
        String userId,

        /** 当前登录用户是否已关注该用户。 */
        boolean following,

        /** 目标用户当前粉丝数，允许短暂最终一致。 */
        int followerCount
) {
}
