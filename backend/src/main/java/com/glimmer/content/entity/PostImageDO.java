package com.glimmer.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_post_image：帖子图片明细。
 */
@Data
@TableName("t_post_image")
public class PostImageDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属帖子 ID。 */
    private Long postId;

    /** 图片 URL。 */
    private String url;

    /** 展示顺序，0 为封面。 */
    private Integer sortOrder;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
