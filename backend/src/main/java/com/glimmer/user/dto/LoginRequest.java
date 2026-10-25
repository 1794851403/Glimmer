package com.glimmer.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 登录 / 自动注册请求。
 */
public record LoginRequest(
        /** 手机号，中国大陆 11 位；登录与自动注册共用，是账号的唯一标识。 */
        @NotBlank(message = "手机号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误")
        String phone,

        /** 短信验证码，6 位数字；校验成功后立即失效，防止重放。 */
        @NotBlank(message = "验证码不能为空")
        @Pattern(regexp = "^\\d{6}$", message = "验证码必须是 6 位数字")
        String code
) {
}
