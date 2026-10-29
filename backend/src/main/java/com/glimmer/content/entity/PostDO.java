package com.glimmer.content.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * t_post：帖子主体。
 */
@Data
@TableName("t_post")
public class PostDO {

    /** 主键，数据库自增；对外接口统一序列化为字符串。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 作者用户 ID。 */
    private Long userId;

    /** 标题，最长 100 字符。 */
    private String title;

    /** 正文，纯文本，最长 20000 字符。 */
    private String content;

    /** 0=已发布待审核, 1=已发布, 2=审核拒绝, 3=已下架。 */
    private Integer status;

    /** 0=正常, 1=作者已删除。 */
    private Integer isDeleted;

    /** 首次发布时间。 */
    private LocalDateTime publishedAt;

    /** 点赞数。 */
    private Integer likeCount;

    /** 评论数。 */
    private Integer commentCount;

    /** 收藏数。 */
    private Integer collectCount;

    /** 浏览数。 */
    private Integer viewCount;

    /** AI 打标状态：0=待处理, 1=成功, 2=规则降级, 3=失败待重试。 */
    private Integer aiTagStatus;

    /** 最近互动时间。 */
    private LocalDateTime lastInteractionAt;

    /** 拒绝 / 下架原因。 */
    private String auditReason;

    /** 最近裁决时间。 */
    private LocalDateTime auditedAt;

    /** 最近裁决管理员。 */
    private Long auditedBy;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 更新时间。 */
    private LocalDateTime updatedAt;
}
