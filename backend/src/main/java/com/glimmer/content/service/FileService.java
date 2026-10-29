package com.glimmer.content.service;

import com.glimmer.content.dto.ImageUploadResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件模块对外服务：目前只有图片上传。
 */
public interface FileService {

    /**
     * 上传一张图片。
     *
     * <p>只做最低限度校验：非空、≤5MB、扩展名白名单；文件名由服务端生成。
     *
     * @param file multipart 文件
     * @return 可访问 URL、大小与 MIME 类型
     */
    ImageUploadResponse uploadImage(MultipartFile file);
}
