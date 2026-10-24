package com.glimmer.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 允许跨域的前端 Origin；仅用于本地直连后端调试。
 *
 * <p>生产 / 演示走 Nginx 同源反代，不配置白名单；开发白名单放在 application-dev.yml。
 * 不使用通配符，也不允许携带 Cookie（Bearer Token）。
 */
@ConfigurationProperties(prefix = "glimmer.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        if (allowedOrigins == null) {
            allowedOrigins = List.of();
        }
    }
}
