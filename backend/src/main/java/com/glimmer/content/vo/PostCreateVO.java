package com.glimmer.content.vo;

import com.glimmer.content.entity.PostDO;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 发布帖子响应。
 *
 * <p>ID 按接口约定序列化为字符串，时间用 epoch 毫秒。
 */
public record PostCreateVO(
        /** 帖子 ID，以字符串序列化，避免前端 Long 精度丢失。 */
        String id,

        /** 帖子状态：0 已发布待审核，1 已发布。 */
        int status,

        /** 中文状态。 */
        String statusText,

        /** 首次发布时间，epoch 毫秒。 */
        long publishedAt,

        /** 可直接展示给用户的发布结果说明。 */
        String message
) {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    public static PostCreateVO from(PostDO post) {
        int status = post.getStatus() == null ? 0 : post.getStatus();
        return new PostCreateVO(
                String.valueOf(post.getId()),
                status,
                statusText(status),
                epochMillis(post.getPublishedAt()),
                resultMessage(status)
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

    private static String resultMessage(int status) {
        return status == 0
                ? "发布成功，内容已公开，审核结果将另行通知"
                : "发布成功";
    }

    private static long epochMillis(LocalDateTime time) {
        return time == null ? 0L : time.atZone(ZONE).toInstant().toEpochMilli();
    }
}
