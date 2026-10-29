package com.glimmer.content.vo;

import com.glimmer.content.entity.TagDO;

/**
 * 标签出参，用于标签列表、帖子标签与帖子详情。
 */
public record TagVO(
        /** 标签 ID，以字符串序列化。 */
        String id,

        /** 标签名。 */
        String name
) {

    public static TagVO from(TagDO tag) {
        return new TagVO(String.valueOf(tag.getId()), tag.getName());
    }
}
