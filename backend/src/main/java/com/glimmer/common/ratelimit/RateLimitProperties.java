package com.glimmer.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Map;

/**
 * 限流阈值；全部走配置，不在代码里写死。
 */
@ConfigurationProperties(prefix = "glimmer.rate-limit")
public record RateLimitProperties(Map<String, Rule> actions) {

    public RateLimitProperties {
        if (actions == null) {
            actions = Map.of();
        }
    }

    public Rule rule(RateLimitAction action) {
        Rule rule = actions.get(action.key());
        if (rule == null) {
            throw new IllegalStateException("缺少限流配置: glimmer.rate-limit.actions." + action.key());
        }
        return rule;
    }

    public record Rule(int limit, Duration window) {

        public Rule {
            if (limit <= 0) {
                throw new IllegalArgumentException("限流阈值必须大于 0");
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("限流窗口必须大于 0");
            }
        }
    }
}
