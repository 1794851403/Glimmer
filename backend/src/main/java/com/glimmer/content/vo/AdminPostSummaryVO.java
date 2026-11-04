package com.glimmer.content.vo;

import com.glimmer.user.vo.UserBriefVO;
import lombok.Builder;

import java.util.List;

/**
 * 管理端帖子摘要。
 *
 * <p>字段与 {@link MyPostSummaryVO} 对齐，额外补充图片数量和最近裁决信息。
 * 管理端不关心当前管理员的点赞 / 收藏状态，{@code liked} / {@code collected} 固定为 false。
 */
@Builder
public record AdminPostSummaryVO(
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

        /** 管理端固定为 false。 */
        boolean liked,

        /** 管理端固定为 false。 */
        boolean collected,

        /** 图片数量。 */
        int imageCount,

        /** 最近裁决时间，epoch 毫秒；从未裁决时为 null。 */
        Long auditedAt,

        /** 最近裁决管理员 ID；从未裁决时为 null。 */
        String auditedBy
) {
}
