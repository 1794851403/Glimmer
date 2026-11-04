package com.glimmer.content.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.glimmer.user.vo.UserBriefVO;
import lombok.Builder;

import java.util.List;

/**
 * 帖子详情出参。
 *
 * <p>公开访问时不返回审核原因、AI 打标状态与管理端裁决字段，因此对空字段使用 NON_NULL 省略。
 */
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PostDetailVO(
        /** 帖子 ID，以字符串序列化。 */
        String id,

        /** 标题。 */
        String title,

        /** 正文，保留原始换行。 */
        String content,

        /** 封面图 URL；无图时为空字符串。 */
        String coverUrl,

        /** 图片明细，按 sortOrder 升序。 */
        List<ImageItemVO> images,

        /** 作者简要信息。 */
        UserBriefVO author,

        /** 标签列表。 */
        List<TagVO> tags,

        /** 0=已发布待审核, 1=已发布, 2=审核拒绝, 3=已下架。 */
        int status,

        /** 中文状态。 */
        String statusText,

        /** 审核拒绝 / 下架原因；仅作者本人或管理员可见。 */
        String auditReason,

        /** 点赞数。 */
        int likeCount,

        /** 评论数。 */
        int commentCount,

        /** 收藏数。 */
        int collectCount,

        /** 浏览数。 */
        int viewCount,

        /** 首次发布时间，epoch 毫秒。 */
        long publishedAt,

        /** 创建时间，epoch 毫秒。 */
        long createdAt,

        /** 更新时间，epoch 毫秒。 */
        long updatedAt,

        /** 当前登录用户是否已点赞。 */
        boolean liked,

        /** 当前登录用户是否已收藏。 */
        boolean collected,

        /** 当前登录用户是否已关注作者。 */
        @JsonProperty("isFollowingAuthor") boolean isFollowingAuthor,

        /** 作者本人且 status IN (0,1,2) 时可编辑。 */
        boolean canEdit,

        /** 作者本人且 status=2 时可直接重提。 */
        boolean canResubmit,

        /** 作者本人且未删除时可删除。 */
        boolean canDelete,

        /** AI 打标状态；仅作者本人或管理员可见。 */
        Integer aiTagStatus,

        /** 最近裁决时间，epoch 毫秒；仅管理端详情返回。 */
        Long auditedAt,

        /** 最近裁决管理员 ID；仅管理端详情返回。 */
        String auditedBy,

        /** 当前管理员是否可以审核 / 下架 / 恢复；仅管理端详情返回。 */
        Boolean canAudit
) {
}
