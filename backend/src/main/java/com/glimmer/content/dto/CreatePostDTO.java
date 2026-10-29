package com.glimmer.content.dto;

import java.util.List;

/**
 * 发布帖子入参。
 *
 * <p>标签 ID 使用字符串，避免前端 Long 精度丢失；图片 URL 由上传接口返回。
 */
public record CreatePostDTO(
        /** 标题，去首尾空白后 1~100 字符。 */
        String title,

        /** 正文，纯文本，1~20000 字符，保留原始换行。 */
        String content,

        /** 图片 URL，1~9 张，数组顺序即展示顺序，第一张为封面。 */
        List<String> imageUrls,

        /** 标签 ID，0~5 个；不传或为空表示暂不打标签。 */
        List<String> tagIds
) {
}
