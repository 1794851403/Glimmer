package com.glimmer.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图片存储配置。{@code type=local} 写本地目录，{@code type=oss} 使用阿里云 OSS。
 */
@ConfigurationProperties(prefix = "glimmer.storage")
public record StorageProperties(
        /** local / oss；默认 local。 */
        String type,

        /** 本地存储配置。 */
        Local local,

        /** OSS 配置。 */
        Oss oss
) {

    public boolean isOss() {
        return "oss".equalsIgnoreCase(type);
    }

    public record Local(
            /** 本地根目录，默认 ./uploads。 */
            String rootDir,

            /** 对外访问前缀，默认 http://localhost:8080。 */
            String publicBaseUrl
    ) {
    }

    public record Oss(
            /** 例如 oss-cn-beijing.aliyuncs.com，可带或不带 scheme。 */
            String endpoint,

            String bucket,

            String accessKeyId,

            String accessKeySecret,

            /** 可选 CDN / 自定义域名；为空时用 https://{bucket}.{endpoint}。 */
            String publicBaseUrl
    ) {
    }
}
