package com.glimmer.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 验证码发送结果；debugCode 只允许 local / dev profile 返回。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SmsCodeResponse(
        /** 验证码有效期，单位秒（默认 300，即 5 分钟）。 */
        int expireIn,

        /** 重新发送冷却时间，单位秒（默认 60）；冷却期内重复请求返回 42901。 */
        int resendAfter,

        /** 调试用明文验证码，仅 local / dev profile 返回；生产环境为 null 且不参与序列化。 */
        String debugCode
) {
}
