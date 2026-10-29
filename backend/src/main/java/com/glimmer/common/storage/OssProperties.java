package com.glimmer.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 阿里云 OSS 配置。开发与发布都使用 OSS，不再提供本地存储实现。
 */
@ConfigurationProperties(prefix = "glimmer.storage.oss")
public record OssProperties(
        /** 例如 oss-cn-beijing.aliyuncs.com，可带或不带 scheme。 */
        String endpoint,

        /** 存储桶名称；开发与发布共用一个桶。 */
        String bucket,

        String accessKeyId,

        String accessKeySecret,

        /** 可选 CDN / 自定义域名；为空时用 https://{bucket}.{endpoint}。 */
        String publicBaseUrl
) {
}
