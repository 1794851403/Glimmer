package com.glimmer.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_tag：标签字典。
 */
@Data
@TableName("t_tag")
public class TagDO {

    /** 主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 标签名，唯一。 */
    private String name;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}
