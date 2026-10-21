package com.glimmer.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 业务错误码。数字与 04-接口设计文档 §1.8 保持一致。
 */
public enum ErrorCode {

    OK(0, HttpStatus.OK, "ok"),

    INVALID_PARAM(40001, HttpStatus.BAD_REQUEST, "参数缺失或格式错误"),
    INVALID_PHONE(40002, HttpStatus.BAD_REQUEST, "手机号格式错误"),
    INVALID_SMS_CODE(40003, HttpStatus.BAD_REQUEST, "验证码错误或已过期"),
    CONTENT_INVALID(40004, HttpStatus.BAD_REQUEST, "文本为空、超长或包含非法内容"),

    UNAUTHENTICATED(40101, HttpStatus.UNAUTHORIZED, "未登录或登录已失效"),
    TOKEN_EXPIRED(40102, HttpStatus.UNAUTHORIZED, "登录已过期，请重新登录"),

    FORBIDDEN(40301, HttpStatus.FORBIDDEN, "无权限访问该资源"),
    ACCOUNT_BANNED(40302, HttpStatus.FORBIDDEN, "账号已被封禁"),
    AUDIT_SELF_FORBIDDEN(40303, HttpStatus.FORBIDDEN, "管理员不能审核自己的帖子"),

    NOT_FOUND(40401, HttpStatus.NOT_FOUND, "资源不存在"),
    POST_NOT_VISIBLE(40402, HttpStatus.NOT_FOUND, "帖子不存在或当前不可见"),
    COMMENT_NOT_FOUND(40403, HttpStatus.NOT_FOUND, "评论不存在或已删除"),

    STATE_CONFLICT(40901, HttpStatus.CONFLICT, "当前状态不允许该操作"),
    CONCURRENT_UPDATE(40903, HttpStatus.CONFLICT, "并发更新失败，请刷新后重试"),
    AUDIT_STATE_CHANGED(40904, HttpStatus.CONFLICT, "帖子审核状态已变化"),

    RATE_LIMITED(42901, HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁，请稍后再试"),

    SECKILL_NOT_STARTED(43001, HttpStatus.CONFLICT, "秒杀尚未开始"),
    SECKILL_ENDED(43002, HttpStatus.CONFLICT, "秒杀已结束"),
    SECKILL_SOLD_OUT(43003, HttpStatus.CONFLICT, "已售罄"),
    SECKILL_LIMIT_EXCEEDED(43004, HttpStatus.CONFLICT, "超出每人限购"),
    SECKILL_ACTIVITY_OFFLINE(43005, HttpStatus.CONFLICT, "活动已下架"),

    AI_UNAVAILABLE(44001, HttpStatus.SERVICE_UNAVAILABLE, "AI 服务暂不可用，请稍后重试"),
    AI_SESSION_NOT_FOUND(44002, HttpStatus.NOT_FOUND, "AI 会话不存在或已过期"),
    AI_BUSY(44003, HttpStatus.TOO_MANY_REQUESTS, "已有进行中的回答，请先停止或等待完成"),
    AI_QUESTION_TOO_LONG(44004, HttpStatus.BAD_REQUEST, "问题超出长度限制"),

    UPLOAD_TYPE_NOT_ALLOWED(45001, HttpStatus.BAD_REQUEST, "图片类型不支持"),
    UPLOAD_TOO_LARGE(45002, HttpStatus.PAYLOAD_TOO_LARGE, "图片超过 5MB"),
    UPLOAD_COUNT_EXCEEDED(45003, HttpStatus.BAD_REQUEST, "图片数量超过 9 张"),

    INTERNAL_ERROR(50000, HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误"),
    DEPENDENCY_UNAVAILABLE(50301, HttpStatus.SERVICE_UNAVAILABLE, "依赖服务暂不可用");

    private final int code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(int code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public int code() {
        return code;
    }

    public HttpStatus status() {
        return status;
    }

    public String message() {
        return message;
    }
}
