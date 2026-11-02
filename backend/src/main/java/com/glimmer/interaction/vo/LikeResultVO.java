package com.glimmer.interaction.vo;

/**
 * 点赞 / 取消点赞响应。
 */
public record LikeResultVO(
        /** 帖子 ID，以字符串序列化。 */
        String postId,

        /** 当前登录用户是否已点赞。 */
        boolean liked,

        /** 帖子当前点赞数。 */
        int likeCount
) {
}
