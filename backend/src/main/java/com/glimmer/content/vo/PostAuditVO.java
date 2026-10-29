package com.glimmer.content.vo;

import com.glimmer.content.entity.PostDO;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 管理端审核操作响应。
 *
 * <p>MVP 先返回审核结果字段，管理端列表 / 详情与完整 {@code AdminPostDetail} 随后实现。
 */
public record PostAuditVO(
        /** 帖子 ID，以字符串序列化。 */
        String id,

        /** 操作后的状态：0/1/2/3。 */
        int status,

        /** 中文状态。 */
        String statusText,

        /** 拒绝 / 下架原因；通过 / 恢复时为 null。 */
        String auditReason,

        /** 首次发布时间，epoch 毫秒。 */
        long publishedAt,

        /** 本次裁决时间，epoch 毫秒。 */
        long auditedAt,

        /** 裁决管理员 ID，以字符串序列化。 */
        String auditedBy
) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static PostAuditVO from(PostDO post) {
        int status = post.getStatus() == null ? 0 : post.getStatus();
        return new PostAuditVO(
                String.valueOf(post.getId()),
                status,
                statusText(status),
                post.getAuditReason(),
                epochMillis(post.getPublishedAt()),
                epochMillis(post.getAuditedAt()),
                post.getAuditedBy() == null ? null : String.valueOf(post.getAuditedBy())
        );
    }

    public static PostAuditVO of(PostDO post, int status, String auditReason,
                                 LocalDateTime auditedAt, Long auditedBy) {
        return new PostAuditVO(
                String.valueOf(post.getId()),
                status,
                statusText(status),
                auditReason,
                epochMillis(post.getPublishedAt()),
                epochMillis(auditedAt),
                auditedBy == null ? null : String.valueOf(auditedBy)
        );
    }

    private static String statusText(int status) {
        return switch (status) {
            case 0 -> "已发布待审核";
            case 1 -> "已发布";
            case 2 -> "审核拒绝";
            case 3 -> "已下架";
            default -> "未知状态";
        };
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
