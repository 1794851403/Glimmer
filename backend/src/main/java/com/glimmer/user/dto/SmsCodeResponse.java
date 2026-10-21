package com.glimmer.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 验证码发送结果；debugCode 只允许 local / dev profile 返回。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmsCodeResponse(int expireIn, int resendAfter, String debugCode) {
}
