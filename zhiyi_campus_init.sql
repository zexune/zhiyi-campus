-- This file is UTF-8. Set the connection charset before any non-ASCII SQL.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- 🎓 智易校园 - 数据库初始化脚本
-- 版本：v3.1（并发/时序问题根本修复版）
-- 日期：2026-08-23
-- 数据库：MySQL 9.7 LTS
-- 字符集：utf8mb4（完整支持中文 + Emoji）
--
-- 本版核心约束：
-- - item.status 增加 RESERVED（交易中）；trade_order 生成列唯一索引
--   uk_order_active_item 保证"一个商品最多一笔进行中订单"（item_reservation 淘汰）
-- - wallet_log 增加 (order_id, user_id, type) 唯一键防止重复资金流水
-- - idempotency_record：资金操作幂等唯一来源（owner_token 协议）
-- - outbox_event + chat_message.source_event_id：事务性系统消息
-- - login_attempt：登录/密保失败限流数据库化（替代本地 Caffeine）
-- - sys_user：profile_version 乐观并发；BANNED_TEMP 必须有到期时间（CHECK）；
--   is_system 单例约束（SYSTEM 技术主体与人工管理员分离）
-- - item_view_stat / view_flush：浏览量脱离 item 业务行（flush_id 幂等）
-- - chat_response_sample / user_reputation_metric：响应速度固定成本派生指标
-- - listing_revision + 发布时固化的层级键：Feed 游标快照基础
-- ============================================================

-- -----------------------------------------------------------
-- 1. 创建数据库
-- -----------------------------------------------------------
DROP DATABASE IF EXISTS zhiyi_campus;
CREATE DATABASE zhiyi_campus
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE zhiyi_campus;

-- ============================================================
-- 2. 建表
-- ============================================================

-- -----------------------------------------------------------
-- 2.0 school — 学校字典表（模块一创新功能：学校隔离 / 邮箱后缀规则）
--     置于 sys_user 之前：sys_user.school_id 外键依赖本表
-- -----------------------------------------------------------
CREATE TABLE school (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '学校ID',
    name            VARCHAR(100)    NOT NULL                 COMMENT '学校名称',
    code            VARCHAR(20)     NOT NULL                 COMMENT '学校代码（如 SHU）',
    email_domain    VARCHAR(100)    DEFAULT NULL             COMMENT '学校邮箱后缀（如 @shu.edu.cn），用于资料一致性校验',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code),
    CONSTRAINT chk_school_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学校字典表';


-- -----------------------------------------------------------
-- 2.1 sys_user — 用户表
-- -----------------------------------------------------------
CREATE TABLE sys_user (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '用户ID',
    student_id      VARCHAR(20)     NOT NULL                 COMMENT '学号（登录凭证）',
    password        VARCHAR(255)    NOT NULL                 COMMENT 'BCrypt加密密码',
    nickname        VARCHAR(50)     NOT NULL                 COMMENT '昵称',
    phone           VARCHAR(20)     DEFAULT NULL             COMMENT '手机号',
    school_id       BIGINT          NOT NULL                 COMMENT '所属学校ID（普通功能按学校隔离；管理员仅为关系约束保留）',
    school_email    VARCHAR(100)    DEFAULT NULL             COMMENT '学校邮箱（可选，后缀须与所属学校匹配）',
    campus          VARCHAR(50)     DEFAULT NULL             COMMENT '校区（个人中心自愿补全，智能推荐与信任标签使用）',
    college         VARCHAR(50)     DEFAULT NULL             COMMENT '学院（个人中心自愿补全，信任标签用）',
    grade           VARCHAR(10)     DEFAULT NULL             COMMENT '年级（个人中心自愿补全，信任标签用）',
    dormitory       VARCHAR(50)     DEFAULT NULL             COMMENT '宿舍楼（个人中心自愿补全，信任标签用）',
    campus_key      VARCHAR(50) GENERATED ALWAYS AS (LOWER(REPLACE(TRIM(campus), ' ', ''))) STORED COMMENT '校区规范化索引键',
    dormitory_key   VARCHAR(50) GENERATED ALWAYS AS (LOWER(REPLACE(TRIM(dormitory), ' ', ''))) STORED COMMENT '宿舍楼规范化索引键',
    role            VARCHAR(20)     NOT NULL DEFAULT 'USER'  COMMENT '角色：USER/ADMIN',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/BANNED_TEMP/BANNED_PERM/CANCELLED（已注销）',
    ban_until_time  DATETIME(6)     DEFAULT NULL             COMMENT '封禁截止时间（数据库时间；BANNED_TEMP 必填，其他状态必须为 NULL）',
    token_version   INT             NOT NULL DEFAULT 0       COMMENT 'Token版本：改密、重置、封禁和注销时原子递增',
    profile_version BIGINT          NOT NULL DEFAULT 0       COMMENT '资料乐观并发版本：仅资料修改推进，与钱包/状态/Token 写入无关',
    is_system       TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否 SYSTEM 技术主体（不可登录、不可交易；全库恰好一个）',
    system_singleton TINYINT        GENERATED ALWAYS AS (CASE WHEN is_system = 1 THEN 1 ELSE NULL END) STORED COMMENT 'SYSTEM 单例约束列',
    level           INT             NOT NULL DEFAULT 1       COMMENT '用户等级',
    exp             INT             NOT NULL DEFAULT 0       COMMENT '累计经验值',
    wallet_balance  DECIMAL(10,2)   NOT NULL DEFAULT 0.00    COMMENT '钱包余额',
    security_question VARCHAR(255)  NOT NULL                 COMMENT '密保问题',
    security_answer  VARCHAR(255)   NOT NULL                 COMMENT '密保答案（BCrypt加密）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY  uk_school_student (school_id, student_id),
    UNIQUE KEY  uk_system_singleton (system_singleton),
    INDEX       idx_user_role_status (role, status, id),
    INDEX       idx_user_status_ban (status, ban_until_time, id),
    INDEX       idx_user_school_campus (school_id, campus_key, id),
    INDEX       idx_user_school_dormitory (school_id, dormitory_key, id),
    INDEX       idx_user_system (is_system),
    CONSTRAINT  fk_user_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT  chk_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT  chk_user_status CHECK (status IN ('ACTIVE', 'BANNED_TEMP', 'BANNED_PERM', 'CANCELLED')),
    CONSTRAINT  chk_user_ban_time CHECK (
        (status = 'BANNED_TEMP' AND ban_until_time IS NOT NULL)
        OR (status <> 'BANNED_TEMP' AND ban_until_time IS NULL)
    ),
    CONSTRAINT  chk_is_system CHECK (is_system IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';


-- -----------------------------------------------------------
-- 2.2 category — 大类字典表
-- -----------------------------------------------------------
CREATE TABLE category (
    id          BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '分类ID',
    name        VARCHAR(50) NOT NULL                 COMMENT '分类名称',
    icon        VARCHAR(50) DEFAULT NULL             COMMENT '图标标识',
    sort_order  INT         NOT NULL DEFAULT 0       COMMENT '排序权重',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品大类字典表';


-- -----------------------------------------------------------
-- 2.3 item — 商品/需求表
--     status 增加 RESERVED：订单生命周期事实在 trade_order，
--     item.status 是可交易性的唯一权威来源（RESERVED 恰好对应一笔 WAITING_MEET 订单）。
--     浏览量迁移到 item_view_stat；listing_revision 由 feed_sequence 全局分配，
--     任何影响 Feed 资格/筛选/排序的编辑都会推进 revision，使商品退出旧游标快照。
--     publisher_campus_key / publisher_dormitory_key 在发布/编辑时从发布者资料固化，
--     分层推荐不再回查 sys_user，用户改资料不会移动进行中的 Feed 链。
-- -----------------------------------------------------------
CREATE TABLE item (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '商品ID',
    publisher_id    BIGINT          NOT NULL                 COMMENT '发布者ID',
    school_id       BIGINT          NOT NULL                 COMMENT '所属学校ID（发布时从用户资料固化）',
    type            VARCHAR(10)     NOT NULL                 COMMENT '类型：SELL出售/BUY求购/SWAP换物/ERRAND跑腿',
    title           VARCHAR(100)    NOT NULL                 COMMENT '商品标题',
    description     TEXT            NOT NULL                 COMMENT '商品描述',
    category_id     BIGINT          NOT NULL                 COMMENT '所属大类ID',
    price           DECIMAL(10,2)   DEFAULT NULL             COMMENT '价格/跑腿悬赏；SWAP为空',
    images          JSON            NOT NULL                 COMMENT '图片URL列表（JSON数组）',
    moderation_status VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT '内容审核状态：PASSED/PENDING/REJECTED',
    trade_location  VARCHAR(255)    DEFAULT NULL             COMMENT '交易地点',
    pickup_location VARCHAR(255)    DEFAULT NULL             COMMENT '跑腿取件地点',
    delivery_location VARCHAR(255)  DEFAULT NULL             COMMENT '跑腿送达地点',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ON_SALE' COMMENT '商品状态：ON_SALE/RESERVED交易中/SOLD/OFF_SHELF',
    feed_key        BIGINT UNSIGNED NOT NULL                 COMMENT '稳定随机推荐序键，发布时生成，同一 listing revision 内不可变',
    listing_revision BIGINT         NOT NULL DEFAULT 0       COMMENT 'Feed 全局单调版本：影响 Feed 资格/排序的编辑与重新上架分配新值',
    publisher_campus_key   VARCHAR(50) DEFAULT NULL          COMMENT '发布时固化的校区层级键（小写去空格）',
    publisher_dormitory_key VARCHAR(50) DEFAULT NULL         COMMENT '发布时固化的宿舍楼层级键（小写去空格）',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '软删除标记：0正常/1已删除',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    INDEX idx_item_market_latest (school_id, status, moderation_status, is_deleted, created_at DESC, id DESC),
    INDEX idx_item_market_feed (school_id, status, moderation_status, is_deleted, feed_key, id),
    INDEX idx_item_market_price (school_id, status, moderation_status, is_deleted, price, id),
    INDEX idx_item_category_latest (school_id, category_id, status, moderation_status, is_deleted, created_at DESC, id DESC),
    INDEX idx_item_type_latest (school_id, type, status, moderation_status, is_deleted, created_at DESC, id DESC),
    INDEX idx_item_publisher_created (publisher_id, created_at DESC, id DESC),
    CONSTRAINT fk_item_publisher  FOREIGN KEY (publisher_id) REFERENCES sys_user(id),
    CONSTRAINT fk_item_school     FOREIGN KEY (school_id)    REFERENCES school(id),
    CONSTRAINT fk_item_category   FOREIGN KEY (category_id)  REFERENCES category(id),
    CONSTRAINT chk_item_type CHECK (type IN ('SELL', 'BUY', 'SWAP', 'ERRAND')),
    CONSTRAINT chk_item_status CHECK (status IN ('ON_SALE', 'RESERVED', 'SOLD', 'OFF_SHELF')),
    CONSTRAINT chk_item_moderation CHECK (moderation_status IN ('PASSED', 'PENDING', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品/需求表';

-- 标签规范化存储：标签筛选通过等值索引与关联表完成，不再扫描 JSON/TEXT。
CREATE TABLE tag (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    name            VARCHAR(50)  NOT NULL COMMENT '展示名称',
    normalized_name VARCHAR(50)  NOT NULL COMMENT 'NFKC + 小写后的唯一检索键',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tag_normalized_name (normalized_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标准标签表';

CREATE TABLE item_tag (
    item_id BIGINT NOT NULL,
    tag_id  BIGINT NOT NULL,
    PRIMARY KEY (item_id, tag_id),
    INDEX idx_item_tag_tag_item (tag_id, item_id),
    CONSTRAINT fk_item_tag_item FOREIGN KEY (item_id) REFERENCES item(id),
    CONSTRAINT fk_item_tag_tag FOREIGN KEY (tag_id) REFERENCES tag(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品标签关联表';

CREATE TABLE event_topic (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '专题ID',
    title VARCHAR(100) NOT NULL COMMENT '专题名称',
    start_time DATETIME NOT NULL COMMENT '开始时间',
    end_time DATETIME NOT NULL COMMENT '结束时间',
    filter_type VARCHAR(10) DEFAULT NULL COMMENT '商品类型筛选',
    filter_category_id BIGINT DEFAULT NULL COMMENT '分类筛选',
    filter_tags JSON DEFAULT NULL COMMENT '商品标签筛选（JSON数组，任一命中即属于专题）',
    banner_text VARCHAR(255) NOT NULL COMMENT 'Banner文案',
    enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_by BIGINT NOT NULL COMMENT '创建管理员',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_topic_active (enabled, start_time, end_time),
    CONSTRAINT fk_topic_category FOREIGN KEY (filter_category_id) REFERENCES category(id),
    CONSTRAINT fk_topic_creator FOREIGN KEY (created_by) REFERENCES sys_user(id),
    CONSTRAINT chk_topic_filter_type CHECK (filter_type IS NULL OR filter_type IN ('SELL', 'BUY', 'SWAP', 'ERRAND'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='大事件专题配置';

-- -----------------------------------------------------------
-- 2.3b feed_sequence — listing_revision 全局单调序列分配器
--     通过 UPDATE ... LAST_INSERT_ID(atomic increment) 分配，无回退。
-- -----------------------------------------------------------
CREATE TABLE feed_sequence (
    id            TINYINT      NOT NULL DEFAULT 0,
    current_value BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Feed listing revision 全局序列';

INSERT INTO feed_sequence (id, current_value) VALUES (0, 0);

-- -----------------------------------------------------------
-- 2.4 item_favorite — 商品收藏关联表
-- -----------------------------------------------------------
CREATE TABLE item_favorite (
    id          BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    user_id     BIGINT      NOT NULL                 COMMENT '收藏者ID',
    item_id     BIGINT      NOT NULL                 COMMENT '商品ID',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_user_item (user_id, item_id),
    INDEX       idx_item (item_id),
    CONSTRAINT  fk_fav_user  FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT  fk_fav_item  FOREIGN KEY (item_id) REFERENCES item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品收藏关联表';


-- -----------------------------------------------------------
-- 2.5 trade_order — 交易订单表
--     active_item_id 生成列 + 唯一索引：一个商品最多一笔 WAITING_MEET 订单（数据库最后防线）。
--     cancel_reason 显式化：所有 CANCELLED 订单必须有原因，审计语义不依赖 NULL。
-- -----------------------------------------------------------
CREATE TABLE trade_order (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订单ID',
    item_id         BIGINT          NOT NULL                 COMMENT '商品ID',
    buyer_id        BIGINT          NOT NULL                 COMMENT '买家ID',
    seller_id       BIGINT          NOT NULL                 COMMENT '卖家ID',
    price           DECIMAL(10,2)   NOT NULL                 COMMENT '成交价格',
    status          VARCHAR(20)     NOT NULL DEFAULT 'WAITING_MEET' COMMENT '状态：WAITING_MEET/COMPLETED/CANCELLED',
    active_item_id  BIGINT          GENERATED ALWAYS AS (
                        CASE WHEN status = 'WAITING_MEET' THEN item_id ELSE NULL END) STORED
                                                            COMMENT '进行中订单的商品独占键（生成列）',
    cancel_reason   VARCHAR(32)     DEFAULT NULL             COMMENT '取消原因：USER_CANCEL/AUTO_CANCEL/ADMIN_FORCE；非取消状态必须为 NULL',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    completed_at    DATETIME(6)     DEFAULT NULL             COMMENT '完成时间（数据库时间）',
    cancelled_at    DATETIME(6)     DEFAULT NULL             COMMENT '取消时间（数据库时间）',

    PRIMARY KEY (id),
    UNIQUE KEY uk_order_active_item (active_item_id),
    INDEX idx_order_buyer_created (buyer_id, created_at DESC, id DESC),
    INDEX idx_order_buyer_status_created (buyer_id, status, created_at DESC, id DESC),
    INDEX idx_order_seller_created (seller_id, created_at DESC, id DESC),
    INDEX idx_order_seller_status_created (seller_id, status, created_at DESC, id DESC),
    INDEX idx_order_status_completed_item (status, completed_at, item_id, price),
    INDEX idx_order_item_status (item_id, status, id),
    CONSTRAINT fk_order_item   FOREIGN KEY (item_id)   REFERENCES item(id),
    CONSTRAINT fk_order_buyer  FOREIGN KEY (buyer_id)  REFERENCES sys_user(id),
    CONSTRAINT fk_order_seller FOREIGN KEY (seller_id) REFERENCES sys_user(id),
    CONSTRAINT chk_order_status CHECK (status IN ('WAITING_MEET', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT chk_order_cancel_reason CHECK (
        (status = 'CANCELLED' AND cancel_reason IN ('USER_CANCEL', 'AUTO_CANCEL', 'ADMIN_FORCE'))
        OR (status <> 'CANCELLED' AND cancel_reason IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单表';


-- -----------------------------------------------------------
-- 2.6 wallet_log — 钱包资金变动流水表
--     uk_wallet_order_type：同一订单+用户+类型最多一条流水，重复资金操作被数据库拒绝。
--     RECHARGE 流水 order_id 为 NULL，MySQL 唯一索引允许多个 NULL，互不冲突。
-- -----------------------------------------------------------
CREATE TABLE wallet_log (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '流水ID',
    user_id         BIGINT          NOT NULL                 COMMENT '用户ID',
    type            VARCHAR(20)     NOT NULL                 COMMENT '类型：RECHARGE充值/PAYMENT支付/REFUND退款/INCOME收入',
    amount          DECIMAL(10,2)   NOT NULL                 COMMENT '变动金额（正=收入，负=支出）',
    balance_after   DECIMAL(10,2)   NOT NULL                 COMMENT '变动后余额',
    order_id        BIGINT          DEFAULT NULL             COMMENT '关联订单ID（充值时为空）',
    remark          VARCHAR(255)    DEFAULT NULL             COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变动时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_wallet_order_type (order_id, user_id, type),
    INDEX idx_wallet_user_created (user_id, created_at DESC, id DESC),
    INDEX idx_wallet_type_created (type, created_at DESC, id DESC),
    CONSTRAINT fk_wallet_user  FOREIGN KEY (user_id)  REFERENCES sys_user(id),
    CONSTRAINT fk_wallet_order FOREIGN KEY (order_id) REFERENCES trade_order(id),
    CONSTRAINT chk_wallet_type CHECK (type IN ('RECHARGE', 'PAYMENT', 'REFUND', 'INCOME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包资金变动流水表';


-- -----------------------------------------------------------
-- 2.7 idempotency_record — 资金操作幂等记录
--     owner_token 协议：插入时不依赖 affected rows；插入后 FOR UPDATE 重读，
--     token 一致者获得执行权。业务失败整体回滚（含本记录），无 FAILED 状态。
--     唯一键骨架永久保留，归档只能迁移 result_snapshot，不得删除唯一键。
-- -----------------------------------------------------------
CREATE TABLE idempotency_record (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    operation       VARCHAR(32) NOT NULL COMMENT 'RECHARGE/ORDER_CREATE/ORDER_CONFIRM/ORDER_CANCEL',
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    CHAR(64)    NOT NULL COMMENT '规范化完整请求参数的 SHA-256',
    owner_token     CHAR(36)    NOT NULL COMMENT '本次事务随机所有权令牌，不由客户端提供',
    status          VARCHAR(20) NOT NULL DEFAULT 'PROCESSING',
    result_snapshot TEXT        NULL COMMENT '成功响应快照（JSON），含 result_version',
    result_version  INT         NOT NULL DEFAULT 1,
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency (user_id, operation, idempotency_key),
    INDEX idx_idem_created (created_at),
    CONSTRAINT fk_idem_user FOREIGN KEY (user_id) REFERENCES sys_user(id),
    CONSTRAINT chk_idem_status CHECK (status IN ('PROCESSING', 'SUCCESS')),
    CONSTRAINT chk_idem_operation CHECK (operation IN ('RECHARGE', 'ORDER_CREATE', 'ORDER_CONFIRM', 'ORDER_CANCEL')),
    CONSTRAINT chk_idem_key_format CHECK (idempotency_key REGEXP '^[0-9a-fA-F-]{36}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资金操作幂等记录';


-- -----------------------------------------------------------
-- 2.8 chat_message — 聊天消息记录表
--     source_event_id 唯一索引：Outbox 事件的至少一次投递在数据库层幂等。
-- -----------------------------------------------------------
CREATE TABLE chat_message (
    id                BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '消息ID',
    conversation_id   VARCHAR(50)   NOT NULL                 COMMENT '会话ID（格式：小ID_大ID）',
    sender_id         BIGINT        NOT NULL                 COMMENT '发送者ID（系统消息为唯一 SYSTEM 用户）',
    receiver_id       BIGINT        NOT NULL                 COMMENT '接收者ID',
    content           TEXT          NOT NULL                 COMMENT '消息内容',
    related_item_id   BIGINT        DEFAULT NULL             COMMENT '关联商品ID',
    is_read           TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '是否已读：0未读/1已读',
    source_event_id   VARCHAR(64)   DEFAULT NULL             COMMENT '产生本消息的 Outbox 事件ID',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_chat_source_event (source_event_id),
    INDEX idx_conversation (conversation_id, id),
    INDEX idx_chat_receiver_read_conv (receiver_id, conversation_id, is_read, id),
    INDEX idx_chat_receiver_unread (receiver_id, is_read, id),
    INDEX idx_sender_created (sender_id, created_at DESC, id DESC),
    CONSTRAINT fk_chat_sender   FOREIGN KEY (sender_id)       REFERENCES sys_user(id),
    CONSTRAINT fk_chat_receiver FOREIGN KEY (receiver_id)     REFERENCES sys_user(id),
    CONSTRAINT fk_chat_item     FOREIGN KEY (related_item_id) REFERENCES item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息记录表';


-- -----------------------------------------------------------
-- 2.9 outbox_event — 事务性系统消息 Outbox
--     业务数据与事件同事务写入；消费者单事件事务（SKIP LOCKED 领取 → 插消息 → 置 SENT）。
-- -----------------------------------------------------------
CREATE TABLE outbox_event (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    event_id        VARCHAR(64)  NOT NULL,
    aggregate_type  VARCHAR(32)  NOT NULL COMMENT 'USER/ORDER/ITEM',
    aggregate_id    BIGINT       NOT NULL,
    event_type      VARCHAR(64)  NOT NULL COMMENT 'USER_PUNISHED/USER_LEVEL_UP/ORDER_SYSTEM_NOTICE/...',
    payload         JSON         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    attempts        INT          NOT NULL DEFAULT 0,
    next_retry_at   DATETIME(6)  DEFAULT NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at         DATETIME(6)  DEFAULT NULL,

    PRIMARY KEY (id),
    UNIQUE KEY uk_outbox_event_id (event_id),
    INDEX idx_outbox_poll (status, next_retry_at, id),
    CONSTRAINT chk_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事务 Outbox 事件表';


-- -----------------------------------------------------------
-- 2.10 login_attempt — 登录/密保失败限流（数据库状态机，替代本地 Caffeine）
-- -----------------------------------------------------------
CREATE TABLE login_attempt (
    attempt_key       VARCHAR(128) NOT NULL COMMENT '带场景前缀的规范化主体摘要：login/reset/admin/chpw',
    window_started_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '当前失败计数窗口起点',
    fail_count        INT          NOT NULL DEFAULT 0,
    locked_until      DATETIME(6)  DEFAULT NULL COMMENT '数据库时间；NULL 表示未锁定',
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (attempt_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录/密保失败限流';


-- -----------------------------------------------------------
-- 2.11 item_view_stat / view_flush — 浏览量派生统计
--     详情读取只写进程内有界缓冲；刷新快照带唯一 flush_id 与累加同事务提交，
--     结果不明时以同一 flush_id 重试，已持久化计数只增不减。
-- -----------------------------------------------------------
CREATE TABLE item_view_stat (
    item_id     BIGINT      NOT NULL,
    view_count  BIGINT      NOT NULL DEFAULT 0,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (item_id),
    INDEX idx_view_stat_count (view_count, item_id),
    CONSTRAINT fk_view_stat_item FOREIGN KEY (item_id) REFERENCES item(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品浏览量统计（独立于 item 业务行）';

CREATE TABLE view_flush (
    flush_id    CHAR(36)    NOT NULL,
    item_count  INT         NOT NULL DEFAULT 0,
    flushed_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (flush_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='浏览量刷新批次凭据（flush_id 幂等）';


-- -----------------------------------------------------------
-- 2.12 chat_response_sample / user_reputation_metric — 响应速度派生指标
--     唯一贡献键防止重复累计；公开信誉接口只读固定大小指标行，不回退全量扫描。
-- -----------------------------------------------------------
CREATE TABLE chat_response_sample (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    sample_key  VARCHAR(80)  NOT NULL COMMENT '唯一贡献键：conversationId:messageId',
    user_id     BIGINT       NOT NULL COMMENT '被统计的回复者（卖家）',
    gap_seconds BIGINT       NOT NULL COMMENT '首次回复间隔秒数',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_response_sample (sample_key),
    INDEX idx_response_sample_user (user_id, id),
    CONSTRAINT fk_sample_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='响应速度唯一贡献样本';

CREATE TABLE user_reputation_metric (
    user_id           BIGINT   NOT NULL,
    sample_count      INT      NOT NULL DEFAULT 0,
    total_gap_seconds BIGINT   NOT NULL DEFAULT 0,
    updated_at        DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_metric_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='响应速度固定大小汇总指标';


-- -----------------------------------------------------------
-- 2.13 violation_report — 内容审核记录表
-- -----------------------------------------------------------
CREATE TABLE violation_report (
    id                      BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    user_id                 BIGINT          NOT NULL                 COMMENT '发布者ID',
    reporter_id             BIGINT          DEFAULT NULL             COMMENT '用户举报来源的举报人ID',
    original_title          VARCHAR(100)    NOT NULL                 COMMENT '原始标题',
    original_description    TEXT            NOT NULL                 COMMENT '原始描述',
    source                  VARCHAR(30)     NOT NULL                 COMMENT '来源：LOCAL_RULE/USER_REPORT/CORRECTION',
    violation_type          VARCHAR(50)     NOT NULL                 COMMENT '风险或举报类型',
    violation_reason        TEXT            NOT NULL                 COMMENT '本地检测依据或用户举报说明',
    matched_rules           JSON            DEFAULT NULL             COMMENT '命中的本地规则编号（JSON数组）',
    rule_version            VARCHAR(30)     DEFAULT NULL             COMMENT '本地规则集版本',
    status                  VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING/CONFIRMED/DISMISSED/OVERTURNED',
    handler_id              BIGINT          DEFAULT NULL             COMMENT '处理的管理员ID',
    handle_note             VARCHAR(500)    DEFAULT NULL             COMMENT '处理备注',
    item_id                 BIGINT          NOT NULL                 COMMENT '关联商品ID',
    created_at              DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上报时间',
    handled_at              DATETIME(6)     DEFAULT NULL             COMMENT '处理时间',

    PRIMARY KEY (id),
    INDEX idx_violation_status_created (status, created_at DESC, id DESC),
    INDEX idx_violation_source_status_created (source, status, created_at DESC, id DESC),
    INDEX idx_violation_user_status_created (user_id, status, created_at DESC, id DESC),
    INDEX idx_violation_item_status_handled (item_id, status, handled_at DESC, id DESC),
    INDEX idx_violation_reporter_item_status (reporter_id, item_id, status),
    CONSTRAINT fk_vr_user    FOREIGN KEY (user_id)    REFERENCES sys_user(id),
    CONSTRAINT fk_vr_reporter FOREIGN KEY (reporter_id) REFERENCES sys_user(id),
    CONSTRAINT fk_vr_item    FOREIGN KEY (item_id)    REFERENCES item(id),
    CONSTRAINT fk_vr_handler FOREIGN KEY (handler_id) REFERENCES sys_user(id),
    CONSTRAINT chk_violation_source CHECK (source IN ('LOCAL_RULE', 'USER_REPORT', 'CORRECTION')),
    CONSTRAINT chk_violation_status CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED', 'OVERTURNED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='本地规则、用户举报与整改复核记录表';


-- -----------------------------------------------------------
-- 2.14 violation_log — 违规处罚日志表
-- -----------------------------------------------------------
CREATE TABLE violation_log (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    user_id     BIGINT          NOT NULL                 COMMENT '被处罚用户ID',
    admin_id    BIGINT          NOT NULL                 COMMENT '操作管理员ID',
    type        VARCHAR(20)     NOT NULL                 COMMENT '账号封禁类型：BAN_TEMP限时封禁/BAN_PERM永久封禁',
    reason      VARCHAR(500)    NOT NULL                 COMMENT '处罚原因',
    ban_days    INT             DEFAULT NULL             COMMENT '封禁天数（限时封禁时填写）',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '处罚时间',

    PRIMARY KEY (id),
    INDEX idx_user (user_id),
    INDEX idx_admin (admin_id),
    CONSTRAINT fk_vl_user  FOREIGN KEY (user_id)  REFERENCES sys_user(id),
    CONSTRAINT fk_vl_admin FOREIGN KEY (admin_id) REFERENCES sys_user(id),
    CONSTRAINT chk_violation_log_type CHECK (type IN ('BAN_TEMP', 'BAN_PERM'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='违规处罚日志表';


-- -----------------------------------------------------------
-- 2.15 reputation_penalty — 独立信誉处罚记录表
-- -----------------------------------------------------------
CREATE TABLE reputation_penalty (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    report_id   BIGINT          NOT NULL                 COMMENT '关联违规报告ID（一条报告仅一条信誉处罚）',
    user_id     BIGINT          NOT NULL                 COMMENT '被处罚用户ID',
    admin_id    BIGINT          NOT NULL                 COMMENT '操作管理员ID',
    type        VARCHAR(20)     NOT NULL                 COMMENT '处罚类型：CONTENT_WARNING',
    points      INT             NOT NULL                 COMMENT '合规度扣分',
    reason      VARCHAR(500)    NOT NULL                 COMMENT '处罚原因',
    status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/REVOKED',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    revoked_at  DATETIME        DEFAULT NULL             COMMENT '撤销时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_reputation_penalty_report (report_id),
    INDEX idx_reputation_penalty_user_status (user_id, status),
    CONSTRAINT fk_rp_report FOREIGN KEY (report_id) REFERENCES violation_report(id),
    CONSTRAINT fk_rp_user   FOREIGN KEY (user_id)   REFERENCES sys_user(id),
    CONSTRAINT fk_rp_admin  FOREIGN KEY (admin_id)  REFERENCES sys_user(id),
    CONSTRAINT chk_penalty_type CHECK (type IN ('CONTENT_WARNING')),
    CONSTRAINT chk_penalty_status CHECK (status IN ('ACTIVE', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='独立信誉处罚记录表';


-- -----------------------------------------------------------
-- 2.16 violation_appeal — 内容违规申诉表
-- -----------------------------------------------------------
CREATE TABLE violation_appeal (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '申诉ID',
    report_id   BIGINT          NOT NULL                 COMMENT '对应的已确认违规记录',
    item_id     BIGINT          NOT NULL                 COMMENT '关联商品ID',
    user_id     BIGINT          NOT NULL                 COMMENT '申诉卖家ID',
    reason      VARCHAR(500)    NOT NULL                 COMMENT '申诉理由',
    status      VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/APPROVED/REJECTED',
    handler_id  BIGINT          DEFAULT NULL             COMMENT '复核管理员ID',
    handle_note VARCHAR(500)    DEFAULT NULL             COMMENT '复核说明',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申诉时间',
    handled_at  DATETIME(6)     DEFAULT NULL             COMMENT '复核时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_appeal_report (report_id),
    INDEX idx_appeal_status_created (status, created_at),
    INDEX idx_appeal_user_created (user_id, created_at DESC, id DESC),
    CONSTRAINT fk_appeal_report  FOREIGN KEY (report_id)  REFERENCES violation_report(id),
    CONSTRAINT fk_appeal_item    FOREIGN KEY (item_id)    REFERENCES item(id),
    CONSTRAINT fk_appeal_user    FOREIGN KEY (user_id)    REFERENCES sys_user(id),
    CONSTRAINT fk_appeal_handler FOREIGN KEY (handler_id) REFERENCES sys_user(id),
    CONSTRAINT chk_appeal_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品内容违规申诉表';


-- -----------------------------------------------------------
-- 2.17 exp_log — 经验值变动记录表（模块一成长体系）
-- -----------------------------------------------------------
CREATE TABLE exp_log (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '记录ID',
    user_id     BIGINT          NOT NULL                 COMMENT '用户ID',
    delta       INT             NOT NULL                 COMMENT '经验值变动量（例如完成订单奖励）',
    exp_after   INT             NOT NULL                 COMMENT '变动后累计经验',
    level_after INT             NOT NULL                 COMMENT '变动后等级',
    reason      VARCHAR(255)    NOT NULL                 COMMENT '变动原因',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '变动时间',

    PRIMARY KEY (id),
    INDEX idx_exp_user_created (user_id, created_at DESC, id DESC),
    CONSTRAINT fk_exp_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='经验值变动记录表';


-- -----------------------------------------------------------
-- 2.18 trade_review — 交易评价表（模块一创新功能：信誉体系）
-- -----------------------------------------------------------
CREATE TABLE trade_review (
    id          BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '评价ID',
    order_id    BIGINT          NOT NULL                 COMMENT '订单ID（一单一评）',
    reviewer_id BIGINT          NOT NULL                 COMMENT '评价者ID（买家）',
    target_id   BIGINT          NOT NULL                 COMMENT '被评价者ID（卖家）',
    rating      TINYINT         NOT NULL                 COMMENT '评分 1-5 星',
    accurate    TINYINT(1)      NOT NULL DEFAULT 1       COMMENT '描述准确：1 准确 / 0 不符',
    comment     VARCHAR(200)    DEFAULT NULL             COMMENT '评价内容',
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评价时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_order (order_id),
    INDEX idx_review_target_created (target_id, created_at DESC, id DESC),
    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5),
    CONSTRAINT fk_review_order    FOREIGN KEY (order_id)    REFERENCES trade_order(id),
    CONSTRAINT fk_review_reviewer FOREIGN KEY (reviewer_id) REFERENCES sys_user(id),
    CONSTRAINT fk_review_target   FOREIGN KEY (target_id)   REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易评价表';


-- ============================================================
-- 3. 初始数据
-- ============================================================

-- -----------------------------------------------------------
-- 3.0 学校种子数据（贯穿实例：上海大学 / 东华大学）
--     另建一个 DISABLED 的系统学校，仅承载 SYSTEM 技术主体，
--     学校停用使其所有登录入口（普通登录按启用学校校验）天然不可达。
-- -----------------------------------------------------------
INSERT INTO school (name, code, email_domain, status) VALUES
('上海大学', 'SHU', '@shu.edu.cn', 'ACTIVE'),
('东华大学', 'DHU', '@dhu.edu.cn', 'ACTIVE'),
('智易平台系统', 'SYSTEM', NULL, 'DISABLED');

-- -----------------------------------------------------------
-- 3.1 SYSTEM 技术主体（系统消息发送者）
--     不可登录、不可交易；密码为已销毁随机明文的 BCrypt 哈希，
--     且登录被 DISABLED 学校前置拦截。全库恰好一个 is_system=1。
-- -----------------------------------------------------------
INSERT INTO sys_user (student_id, password, nickname, school_id, role, status, level, exp, wallet_balance, security_question, security_answer, is_system)
VALUES (
    '__system__',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    '系统通知',
    (SELECT id FROM school WHERE code = 'SYSTEM'),
    'USER',
    'ACTIVE',
    1,
    0,
    0.00,
    '系统预设问题',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy',
    1
);

-- -----------------------------------------------------------
-- 3.2 唯一人工管理员（仅通过 /admin/login 进入后台；初始化后请立即改密）
--     显式 is_system=0，与 SYSTEM 技术主体彻底分离。
-- -----------------------------------------------------------
INSERT INTO sys_user (student_id, password, nickname, school_id, role, status, level, exp, wallet_balance, security_question, security_answer, is_system)
VALUES (
    'admin',
    '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K',  -- 密码 123456
    '系统管理员',
    (SELECT id FROM school WHERE code = 'SHU'),
    'ADMIN',
    'ACTIVE',
    99,
    0,
    0.00,
    '系统预设问题',
    '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K',  -- 密码 123456
    0
);

-- -----------------------------------------------------------
-- 3.3 预设商品大类（8个）
-- -----------------------------------------------------------
INSERT INTO category (name, icon, sort_order) VALUES
('数码电子', '📱', 1),
('教材书籍', '📚', 2),
('服饰鞋包', '👕', 3),
('生活日用', '🏠', 4),
('运动娱乐', '🎮', 5),
('零食饮品', '🍜', 6),
('学习用品', '📝', 7),
('其他',     '📦', 99);
