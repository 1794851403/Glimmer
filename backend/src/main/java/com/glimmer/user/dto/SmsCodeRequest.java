package com.glimmer.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 发送验证码请求。MVP 注册与登录合一，scene 只允许 LOGIN。
 */
public record SmsCodeRequest(
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
        String phone,

        @Pattern(regexp = "LOGIN", message = "scene 只支持 LOGIN")
        String scene
) {
}
