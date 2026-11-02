package com.glimmer.interaction.vo;

/**
 * 收藏 / 取消收藏响应。
 */
public record CollectResultVO(
        /** 帖子 ID，以字符串序列化。 */
        String postId,

        /** 当前登录用户是否已收藏。 */
        boolean collected,

        /** 帖子当前收藏数。 */
        int collectCount
) {
}
