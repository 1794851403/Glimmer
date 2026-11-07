package com.glimmer.feed.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Feed 收件箱与写扩散配置。
 *
 * <p>全部走配置，默认值与架构文档 §6.5 保持一致：收件箱 200 条、30 天过期、
 * 每批 500 个粉丝、24 小时未读则首页重建。
 */
@ConfigurationProperties(prefix = "glimmer.feed")
public record FeedProperties(
        Integer inboxMaxSize,
        Integer inboxTtlDays,
        Integer fanoutBatchSize,
        Integer rebuildAfterHours
) {

    public FeedProperties {
        if (inboxMaxSize == null || inboxMaxSize < 1) {
            inboxMaxSize = 200;
        }
        if (inboxTtlDays == null || inboxTtlDays < 1) {
            inboxTtlDays = 30;
        }
        if (fanoutBatchSize == null || fanoutBatchSize < 1) {
            fanoutBatchSize = 500;
        }
        if (rebuildAfterHours == null || rebuildAfterHours < 1) {
            rebuildAfterHours = 24;
        }
    }

    public int maxSize() {
        return inboxMaxSize;
    }

    public Duration ttl() {
        return Duration.ofDays(inboxTtlDays);
    }

    public int batchSize() {
        return fanoutBatchSize;
    }

    public Duration rebuildAfter() {
        return Duration.ofHours(rebuildAfterHours);
    }
}
