package com.glimmer.content.vo;

import com.glimmer.user.vo.UserBriefVO;

import java.util.List;

/**
 * 帖子摘要，用于 Feed 等列表。
 *
 * <p>计数直接取数据库计数列；点赞 / 收藏状态在互动模块接入前固定为 false。
 */
public record PostSummaryVO(
        /** 帖子 ID，以字符串序列化。 */
        String id,

        /** 标题。 */
        String title,

        /** 封面图 URL；无图时为空字符串。 */
        String coverUrl,

        /** 作者简要信息。 */
        UserBriefVO author,

        /** 标签列表。 */
        List<TagVO> tags,

        /** 点赞数。 */
        int likeCount,

        /** 评论数。 */
        int commentCount,

        /** 收藏数。 */
        int collectCount,

        /** 浏览数。 */
        int viewCount,

        /** 发布时间，epoch 毫秒。 */
        long publishedAt,

        /** 当前登录用户是否已点赞。 */
        boolean liked,

        /** 当前登录用户是否已收藏。 */
        boolean collected
) {
}
