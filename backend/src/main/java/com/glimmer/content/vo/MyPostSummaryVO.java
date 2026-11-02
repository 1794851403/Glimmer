package com.glimmer.content.vo;

import com.glimmer.user.vo.UserBriefVO;
import lombok.Builder;

import java.util.List;

/**
 * 我的帖子摘要。
 *
 * <p>在 {@link PostSummaryVO} 的基础上增加状态、审核原因与创建 / 更新时间，
 * 供作者在“我的帖子”中查看和筛选。
 */
@Builder
public record MyPostSummaryVO(
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

        /** 0=已发布待审核, 1=已发布, 2=审核拒绝, 3=已下架。 */
        int status,

        /** 中文状态。 */
        String statusText,

        /** 拒绝 / 下架原因；其他状态为 null。 */
        String auditReason,

        /** 创建时间，epoch 毫秒。 */
        long createdAt,

        /** 更新时间，epoch 毫秒。 */
        long updatedAt,

        /** 当前登录用户是否已点赞；互动模块接入前固定为 false。 */
        boolean liked,

        /** 当前登录用户是否已收藏；互动模块接入前固定为 false。 */
        boolean collected
) {
}
