package com.glimmer.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_post_tag：帖子与标签的关系。
 */
@Data
@TableName("t_post_tag")
public class PostTagDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 帖子 ID。 */
    private Long postId;

    /** 标签 ID。 */
    private Long tagId;

    /** 来源：0=MANUAL, 1=AI, 2=RULE。 */
    private Integer source;

    /** AI Prompt 版本，手工标签为空。 */
    private String promptVersion;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
