package com.glimmer.common.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * JWT 配置。密钥只能来自环境变量或本地 .env，不写入仓库。
 */
@ConfigurationProperties(prefix = "glimmer.jwt")
public record JwtProperties(String secret, Duration expire) {

    public JwtProperties {
        if (expire == null) {
            expire = Duration.ofDays(7);
        }
    }
}
