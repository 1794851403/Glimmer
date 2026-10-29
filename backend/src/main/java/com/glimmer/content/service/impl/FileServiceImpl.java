package com.glimmer.content.service.impl;

import com.glimmer.common.exception.BizException;
import com.glimmer.common.exception.ErrorCode;
import com.glimmer.common.storage.StorageService;
import com.glimmer.common.web.UserContext;
import com.glimmer.content.dto.ImageUploadResponse;
import com.glimmer.content.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * 图片上传实现：校验扩展名与大小，生成对象名后交给 {@link StorageService}。
 *
 * <p>按当前 MVP 口径，不做 magic bytes 校验，也不做图片内容审核。
 */
@Slf4j
@Service
public class FileServiceImpl implements FileService {

    private static final long MAX_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE_PATH = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    private final StorageService storageService;

    public FileServiceImpl(StorageService storageService) {
        this.storageService = storageService;
    }

    @Override
    public ImageUploadResponse uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.UPLOAD_TYPE_NOT_ALLOWED, "图片不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new BizException(ErrorCode.UPLOAD_TOO_LARGE);
        }

        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new BizException(ErrorCode.UPLOAD_TYPE_NOT_ALLOWED);
        }

        String contentType = contentTypeOf(extension);
        String objectName = objectName(extension);

        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            log.error("read upload file failed, objectName={}", objectName, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "读取上传文件失败", e);
        }

        String url = storageService.upload(content, objectName, contentType);
        return new ImageUploadResponse(url, file.getSize(), contentType);
    }

    private String objectName(String extension) {
        String date = LocalDate.now(ZONE).format(DATE_PATH);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return "images/" + UserContext.userId() + "/" + date + "/" + uuid + "." + extension;
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String contentTypeOf(String extension) {
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }
}
