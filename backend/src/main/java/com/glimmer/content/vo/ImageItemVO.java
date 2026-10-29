package com.glimmer.content.vo;

import com.glimmer.content.entity.PostImageDO;

/**
 * 帖子图片明细。
 */
public record ImageItemVO(
        /** 图片 URL。 */
        String url,

        /** 展示顺序，0 为封面。 */
        int sortOrder
) {

    public static ImageItemVO from(PostImageDO image) {
        return new ImageItemVO(image.getUrl(), image.getSortOrder() == null ? 0 : image.getSortOrder());
    }
}
