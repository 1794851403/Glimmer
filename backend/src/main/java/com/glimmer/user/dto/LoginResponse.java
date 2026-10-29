package com.glimmer.user.dto;

import com.glimmer.user.vo.SelfProfileVO;

/**
 * 登录响应：令牌 + 用户信息。
 */
public record LoginResponse(
        /** 令牌类型，固定为 {@code Bearer}；客户端按 {@code Authorization: Bearer <accessToken>} 携带。 */
        String tokenType,

        /** 访问令牌（JWT），后续请求放在 {@code Authorization} 请求头中。 */
        String accessToken,

        /** 访问令牌有效期，单位秒（默认 604800，即 7 天）。 */
        long expiresIn,

        /** 登录用户信息，手机号已脱敏；手机号不存在时后端自动注册，这里就是新账号信息。 */
        SelfProfileVO user
) {
}
