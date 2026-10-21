package com.glimmer.common.api;

import com.glimmer.common.exception.ErrorCode;
import org.slf4j.MDC;

/**
 * 统一响应信封：{@code {code, message, data, traceId}}。
 *
 * <p>除 204 与 SSE 外，所有接口都返回该结构；traceId 由 {@code TraceIdFilter} 写入 MDC。
 */
public record Result<T>(int code, String message, T data, String traceId) {

    public static <T> Result<T> ok(T data) {
        return new Result<>(ErrorCode.OK.code(), ErrorCode.OK.message(), data, currentTraceId());
    }

    public static Result<Void> ok() {
        return ok(null);
    }

    public static <T> Result<T> error(ErrorCode errorCode, String message) {
        String text = (message == null || message.isBlank()) ? errorCode.message() : message;
        return new Result<>(errorCode.code(), text, null, currentTraceId());
    }

    private static String currentTraceId() {
        String traceId = MDC.get("traceId");
        return traceId == null ? "" : traceId;
    }
}
