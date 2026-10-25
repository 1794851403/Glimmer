package com.glimmer.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送验证码请求。MVP 注册与登录合一，scene 只允许 LOGIN。
 */
public record SmsCodeRequest(
        /** 接收验证码的手机号，中国大陆 11 位。 */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
        String phone,

        /** 业务场景，MVP 只支持 {@code LOGIN}；可省略，省略时按 LOGIN 处理（注册与登录合一，没有 REGISTER）。 */
        @Pattern(regexp = "LOGIN", message = "scene 只支持 LOGIN")
        String scene
) {
}
