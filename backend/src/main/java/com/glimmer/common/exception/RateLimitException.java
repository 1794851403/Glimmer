package com.glimmer.common.exception;

/**
 * 限流异常；GlobalExceptionHandler 会补齐 Retry-After / X-RateLimit-* 响应头。
 */
public class RateLimitException extends BizException {

    private final long retryAfterSeconds;
    private final int limit;

    public RateLimitException(long retryAfterSeconds, int limit) {
        super(ErrorCode.RATE_LIMITED);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
        this.limit = limit;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public int limit() {
        return limit;
    }
}
