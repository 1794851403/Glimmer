package com.glimmer.common.storage;

/**
 * 对象存储抽象。业务层只依赖该接口，按配置切换本地目录或 OSS。
 */
public interface StorageService {

    /**
     * 写入一个对象并返回可访问 URL。
     *
     * @param content     文件字节
     * @param objectName  服务端生成的对象名，例如 images/1/2026/10/29/xxx.jpg
     * @param contentType MIME 类型，可为空
     * @return 对外可访问的 URL
     */
    String upload(byte[] content, String objectName, String contentType);
}
