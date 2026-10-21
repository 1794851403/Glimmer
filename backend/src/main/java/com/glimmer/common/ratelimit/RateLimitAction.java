package com.glimmer.common.ratelimit;

/**
 * 需要限流的关键用例。与 application.yml 中 glimmer.rate-limit.actions 的 key 对应。
 */
public enum RateLimitAction {

    SMS_CODE("sms-code", "glimmer:auth:limit:sms:"),
    LOGIN("login", "glimmer:auth:limit:login:"),
    SECKILL("seckill", "glimmer:seckill:limit:"),
    AI("ai", "glimmer:ai:limit:");

    private final String key;
    private final String keyPrefix;

    RateLimitAction(String key, String keyPrefix) {
        this.key = key;
        this.keyPrefix = keyPrefix;
    }

    public String key() {
        return key;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
