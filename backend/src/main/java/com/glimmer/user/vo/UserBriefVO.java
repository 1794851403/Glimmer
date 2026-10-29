package com.glimmer.user.vo;

import com.glimmer.user.entity.UserDO;

/**
 * 用户简要信息，用于帖子作者、评论作者等嵌套结构。
 */
public record UserBriefVO(
        /** 用户 ID，以字符串序列化。 */
        String id,

        /** 昵称。 */
        String nickname,

        /** 头像 URL；未设置时为空字符串。 */
        String avatarUrl
) {

    public static UserBriefVO from(UserDO user) {
        return new UserBriefVO(
                String.valueOf(user.getId()),
                user.getNickname(),
                user.getAvatarUrl() == null ? "" : user.getAvatarUrl()
        );
    }

    public static UserBriefVO placeholder(Long userId) {
        return new UserBriefVO(String.valueOf(userId), "已注销用户", "");
    }
}
