package com.glimmer.content.dto;

/**
 * 图片上传响应：可访问 URL、字节数、MIME 类型。
 */
public record ImageUploadResponse(
        String url,
        long size,
        String contentType
) {
}
