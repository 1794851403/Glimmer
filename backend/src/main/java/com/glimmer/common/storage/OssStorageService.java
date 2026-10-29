package com.glimmer.common.storage;

import com.aliyun.oss.ClientException;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;

/**
 * 阿里云 OSS 上传：开发与发布共用同一个桶，后端中转 putObject。
 *
 * <p>缺少必要配置时启动失败，避免运行期才报错。
 */
@Slf4j
@Service
public class OssStorageService {

    private final OSS ossClient;
    private final String bucket;
    private final String publicBaseUrl;

    public OssStorageService(OssProperties properties) {
        if (properties == null) {
            throw new IllegalStateException("缺少 OSS 配置: glimmer.storage.oss.*");
        }
        String endpoint = normalizeEndpoint(properties.endpoint());
        require(endpoint, "OSS_ENDPOINT");
        require(properties.bucket(), "OSS_BUCKET");
        require(properties.accessKeyId(), "OSS_ACCESS_KEY_ID");
        require(properties.accessKeySecret(), "OSS_ACCESS_KEY_SECRET");

        this.bucket = properties.bucket().trim();
        this.ossClient = new OSSClientBuilder().build(
                endpoint, properties.accessKeyId().trim(), properties.accessKeySecret().trim());
        this.publicBaseUrl = publicBaseUrl(properties.publicBaseUrl(), bucket, endpoint);
    }

    public String upload(byte[] content, String objectName, String contentType) {
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            if (contentType != null && !contentType.isBlank()) {
                metadata.setContentType(contentType);
            }
            // 桶保持私有，只把本次上传的图片设为公共读，前端才能直接访问 URL。
            // 随 PutObject 一起提交 x-oss-object-acl，避免上传成功但 ACL 未设置的半成品对象。
            metadata.setObjectAcl(CannedAccessControlList.PublicRead);
            PutObjectRequest request = new PutObjectRequest(
                    bucket, objectName, new ByteArrayInputStream(content), metadata);
            ossClient.putObject(request);
            return publicBaseUrl + "/" + objectName;
        } catch (OSSException | ClientException e) {
            log.error("oss upload failed, bucket={}, objectName={}", bucket, objectName, e);
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "文件存储暂不可用");
        }
    }

    @PreDestroy
    public void shutdown() {
        ossClient.shutdown();
    }

    private static String publicBaseUrl(String configured, String bucket, String endpoint) {
        if (configured != null && !configured.isBlank()) {
            return stripTrailingSlash(configured.trim());
        }
        return "https://" + bucket + "." + endpoint;
    }

    private static String normalizeEndpoint(String endpoint) {
        if (endpoint == null) {
            return "";
        }
        return endpoint.trim()
                .replaceFirst("^https?://", "")
                .replaceAll("/+$", "");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少 OSS 配置: " + name);
        }
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
