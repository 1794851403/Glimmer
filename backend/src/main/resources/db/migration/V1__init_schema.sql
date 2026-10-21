-- ============================================================
-- 微光 Glimmer — 初始化表结构
-- MySQL 8.0.16+
-- ============================================================

SET NAMES utf8mb4;
SET time_zone = '+08:00';

-- ------------------------------------------------------------
-- 1. 账号
-- ------------------------------------------------------------
CREATE TABLE t_user (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    phone           VARCHAR(20)     NOT NULL COMMENT '手机号',
    nickname        VARCHAR(32)     NOT NULL COMMENT '昵称',
    avatar_url      VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '头像 URL',
    bio             VARCHAR(200)    NOT NULL DEFAULT '' COMMENT '个人简介',
    role            TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=普通用户,1=管理员',
    status          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=正常,1=封禁',
    following_count INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '关注数',
    follower_count  INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '粉丝数',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone),
    KEY idx_status_created (status, created_at),
    KEY idx_nickname (nickname),
    CONSTRAINT chk_t_user_role   CHECK (role IN (0, 1)),
    CONSTRAINT chk_t_user_status CHECK (status IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='账号与身份主表';

CREATE TABLE t_user_follow (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    follower_id BIGINT UNSIGNED NOT NULL COMMENT '关注者',
    followee_id BIGINT UNSIGNED NOT NULL COMMENT '被关注者',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关注时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_follower_followee (follower_id, followee_id),
    KEY idx_followee_created (followee_id, created_at),
    KEY idx_follower_created (follower_id, created_at),
    CONSTRAINT chk_t_user_follow_self CHECK (follower_id <> followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='关注关系';

-- ------------------------------------------------------------
-- 2. 内容
-- ------------------------------------------------------------
CREATE TABLE t_post (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id             BIGINT UNSIGNED NOT NULL COMMENT '作者',
    title               VARCHAR(100)    NOT NULL COMMENT '标题',
    content             TEXT            NOT NULL COMMENT '正文，上限 20000 字符',
    status              TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=已发布待审核,1=已发布,2=审核拒绝,3=已下架',
    is_deleted          TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=正常,1=作者已删除',
    published_at        DATETIME(3)     NOT NULL COMMENT '首次发布时间',
    like_count          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '点赞数',
    comment_count       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '评论数',
    collect_count       INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '收藏数',
    view_count          INT UNSIGNED    NOT NULL DEFAULT 0 COMMENT '浏览数',
    ai_tag_status       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=待处理,1=成功,2=规则降级,3=失败待重试',
    last_interaction_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近互动时间',
    audit_reason        VARCHAR(200)    NULL DEFAULT NULL COMMENT '拒绝/下架原因',
    audited_at          DATETIME(3)     NULL DEFAULT NULL COMMENT '最近裁决时间',
    audited_by          BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '最近裁决管理员',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '内容/状态更新时间',
    PRIMARY KEY (id),
    KEY idx_user_deleted_created (user_id, is_deleted, created_at),
    KEY idx_user_status_deleted_created (user_id, status, is_deleted, created_at),
    KEY idx_user_status_deleted_published (user_id, status, is_deleted, published_at),
    KEY idx_status_deleted_created (status, is_deleted, created_at),
    KEY idx_status_deleted_published (status, is_deleted, published_at),
    KEY idx_status_deleted_interaction (status, is_deleted, last_interaction_at),
    KEY idx_ai_tag_status (ai_tag_status),
    KEY idx_updated_at (updated_at),
    CONSTRAINT chk_t_post_status      CHECK (status IN (0, 1, 2, 3)),
    CONSTRAINT chk_t_post_deleted     CHECK (is_deleted IN (0, 1)),
    CONSTRAINT chk_t_post_ai_status   CHECK (ai_tag_status IN (0, 1, 2, 3))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='帖子主体';

CREATE TABLE t_post_image (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_id    BIGINT UNSIGNED NOT NULL COMMENT '帖子',
    url        VARCHAR(512)    NOT NULL COMMENT '图片 URL',
    sort_order TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '顺序，0 为封面',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_sort (post_id, sort_order),
    CONSTRAINT chk_t_post_image_sort CHECK (sort_order BETWEEN 0 AND 8)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='帖子图片明细';

CREATE TABLE t_tag (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(32)     NOT NULL COMMENT '标签名',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='标签字典';

CREATE TABLE t_post_tag (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_id        BIGINT UNSIGNED NOT NULL COMMENT '帖子',
    tag_id         BIGINT UNSIGNED NOT NULL COMMENT '标签',
    source         TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=MANUAL,1=AI,2=RULE',
    prompt_version VARCHAR(32)     NULL DEFAULT NULL COMMENT 'AI Prompt 版本',
    created_at     DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_tag (post_id, tag_id),
    KEY idx_tag_post (tag_id, post_id),
    KEY idx_created_at (created_at),
    CONSTRAINT chk_t_post_tag_source CHECK (source IN (0, 1, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='帖子-标签关系';

-- ------------------------------------------------------------
-- 3. 互动
-- ------------------------------------------------------------
CREATE TABLE t_like (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '用户',
    post_id    BIGINT UNSIGNED NOT NULL COMMENT '帖子',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '点赞时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_post (user_id, post_id),
    KEY idx_post_user (post_id, user_id),
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='点赞关系';

CREATE TABLE t_collect (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '用户',
    post_id    BIGINT UNSIGNED NOT NULL COMMENT '帖子',
    created_at DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '收藏时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_post (user_id, post_id),
    KEY idx_post_user (post_id, user_id),
    KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='收藏关系';

CREATE TABLE t_comment (
    id                        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    post_id                   BIGINT UNSIGNED NOT NULL COMMENT '帖子',
    user_id                   BIGINT UNSIGNED NOT NULL COMMENT '评论者',
    parent_id                 BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=一级评论，否则为根评论ID',
    reply_to_user_id          BIGINT UNSIGNED NULL DEFAULT NULL COMMENT '被回复用户',
    content                   VARCHAR(1000)   NOT NULL COMMENT '评论内容',
    sentiment                 TINYINT         NULL DEFAULT NULL COMMENT '1=正面,0=中性,-1=负面,NULL=未分析',
    sentiment_prompt_version  VARCHAR(32)     NULL DEFAULT NULL COMMENT '情感分析 Prompt 版本',
    is_deleted                TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=正常,1=已删除',
    created_at                DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at                DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_post_parent_created (post_id, parent_id, is_deleted, created_at),
    KEY idx_user_created (user_id, created_at),
    KEY idx_post_sentiment (post_id, sentiment),
    CONSTRAINT chk_t_comment_sentiment CHECK (sentiment IS NULL OR sentiment IN (-1, 0, 1)),
    CONSTRAINT chk_t_comment_deleted   CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='评论/回复与情感分析';

-- ------------------------------------------------------------
-- 4. 秒杀
-- ------------------------------------------------------------
CREATE TABLE t_seckill_activity (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    title       VARCHAR(100)    NOT NULL COMMENT '活动/商品标题',
    description VARCHAR(500)    NOT NULL DEFAULT '' COMMENT '描述',
    image_url   VARCHAR(512)    NOT NULL DEFAULT '' COMMENT '商品图',
    price       DECIMAL(10,2)   NOT NULL COMMENT '秒杀价',
    total_stock INT UNSIGNED    NOT NULL COMMENT '总库存',
    stock       INT UNSIGNED    NOT NULL COMMENT '剩余库存',
    limit_num   INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '每人限购',
    start_time  DATETIME(3)     NOT NULL COMMENT '开始时间',
    end_time    DATETIME(3)     NOT NULL COMMENT '结束时间',
    status      TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=草稿,1=已发布,2=已下架',
    created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_status_start_end (status, start_time, end_time),
    CONSTRAINT chk_t_seckill_activity_status CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_t_seckill_activity_price  CHECK (price >= 0),
    CONSTRAINT chk_t_seckill_activity_stock  CHECK (stock <= total_stock),
    CONSTRAINT chk_t_seckill_activity_limit  CHECK (limit_num >= 1),
    CONSTRAINT chk_t_seckill_activity_time   CHECK (end_time > start_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='秒杀活动、商品与库存';

CREATE TABLE t_seckill_order (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    order_no     VARCHAR(32)     NOT NULL COMMENT '订单号',
    user_id      BIGINT UNSIGNED NOT NULL COMMENT '用户',
    activity_id  BIGINT UNSIGNED NOT NULL COMMENT '活动',
    quantity     INT UNSIGNED    NOT NULL DEFAULT 1 COMMENT '数量',
    amount       DECIMAL(10,2)   NOT NULL COMMENT '订单金额快照',
    status       TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=待支付,1=已支付,2=已关闭',
    pay_trade_no VARCHAR(64)     NULL DEFAULT NULL COMMENT '支付平台交易号',
    paid_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '支付时间',
    closed_at    DATETIME(3)     NULL DEFAULT NULL COMMENT '关闭时间',
    created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    UNIQUE KEY uk_user_activity (user_id, activity_id),
    KEY idx_user_created (user_id, created_at),
    KEY idx_activity_status (activity_id, status),
    KEY idx_status_created (status, created_at),
    CONSTRAINT chk_t_seckill_order_status   CHECK (status IN (0, 1, 2)),
    CONSTRAINT chk_t_seckill_order_quantity CHECK (quantity >= 1),
    CONSTRAINT chk_t_seckill_order_amount   CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC COMMENT='秒杀订单';
