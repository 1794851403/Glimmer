package com.glimmer.content.controller;

import com.glimmer.common.api.Result;
import com.glimmer.content.dto.ImageUploadResponse;
import com.glimmer.content.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件接口。必须登录，鉴权由 AuthInterceptor 统一完成。
 */
@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 上传一张图片（需登录）。
     *
     * <p>multipart 字段名固定为 {@code file}；支持 jpg / jpeg / png / webp，≤5MB。
     * 成功后返回可访问 URL，可直接用于发帖的 imageUrls 或头像 avatarUrl。
     */
    @PostMapping(value = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ImageUploadResponse> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.ok(fileService.uploadImage(file));
    }
}
