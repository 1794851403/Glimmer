package com.glimmer.common.storage;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 本地目录存储：开发 / 单测使用，文件写到 {@code glimmer.storage.local.root-dir}，
 * 通过 {@code /uploads/**} 静态映射访问。
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "glimmer.storage", name = "type", havingValue = "local", matchIfMissing = true)
public class LocalStorageServiceImpl implements StorageService {

    private static final String DEFAULT_ROOT = "./uploads";
    private static final String DEFAULT_BASE_URL = "http://localhost:8080";

    private final Path root;
    private final String publicBaseUrl;

    public LocalStorageServiceImpl(StorageProperties properties) {
        StorageProperties.Local local = properties.local();
        String rootDir = local == null || local.rootDir() == null || local.rootDir().isBlank()
                ? DEFAULT_ROOT : local.rootDir();
        String baseUrl = local == null || local.publicBaseUrl() == null || local.publicBaseUrl().isBlank()
                ? DEFAULT_BASE_URL : local.publicBaseUrl();

        this.root = Paths.get(rootDir).toAbsolutePath().normalize();
        this.publicBaseUrl = stripTrailingSlash(baseUrl.trim());
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建本地上传目录: " + root, e);
        }
    }

    @Override
    public String upload(byte[] content, String objectName, String contentType) {
        if (objectName == null || objectName.isBlank()
                || objectName.startsWith("/") || objectName.contains("..")) {
            throw new BizException(ErrorCode.INVALID_PARAM, "非法文件路径");
        }

        Path target = root.resolve(objectName).normalize();
        if (!target.startsWith(root)) {
            throw new BizException(ErrorCode.INVALID_PARAM, "非法文件路径");
        }

        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException e) {
            log.error("local upload failed, objectName={}", objectName, e);
            throw new BizException(ErrorCode.DEPENDENCY_UNAVAILABLE, "文件存储暂不可用");
        }
        return publicBaseUrl + "/uploads/" + objectName;
    }

    private static String stripTrailingSlash(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(0, end);
    }
}
