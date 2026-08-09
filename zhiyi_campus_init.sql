-- This file is UTF-8. Set the connection charset before any non-ASCII SQL.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

-- ============================================================
-- 🎓 智易校园 - 数据库初始化脚本
-- 版本：v3.0
-- 日期：2026-08-09
-- 数据库：MySQL 8.0+
-- 字符集：utf8mb4（完整支持中文 + Emoji）
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
    ban_until_time  DATETIME        DEFAULT NULL             COMMENT '封禁截止时间（临时封禁）',
    token_version   INT             NOT NULL DEFAULT 0       COMMENT 'Token版本：改密、重置、封禁和注销时原子递增',
    level           INT             NOT NULL DEFAULT 1       COMMENT '用户等级',
    exp             INT             NOT NULL DEFAULT 0       COMMENT '累计经验值',
    wallet_balance  DECIMAL(10,2)   NOT NULL DEFAULT 0.00    COMMENT '钱包余额',
    security_question VARCHAR(255)  NOT NULL                 COMMENT '密保问题',
    security_answer  VARCHAR(255)   NOT NULL                 COMMENT '密保答案（BCrypt加密）',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY  uk_school_student (school_id, student_id),
    INDEX       idx_user_role_status (role, status, id),
    INDEX       idx_user_status_ban (status, ban_until_time, id),
    INDEX       idx_user_school_campus (school_id, campus_key, id),
    INDEX       idx_user_school_dormitory (school_id, dormitory_key, id),
    CONSTRAINT  fk_user_school FOREIGN KEY (school_id) REFERENCES school(id),
    CONSTRAINT  chk_user_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT  chk_user_status CHECK (status IN ('ACTIVE', 'BANNED_TEMP', 'BANNED_PERM', 'CANCELLED'))
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
    status          VARCHAR(20)     NOT NULL DEFAULT 'ON_SALE' COMMENT '商品状态：ON_SALE/SOLD/OFF_SHELF；订单状态独立存储',
    feed_key        BIGINT UNSIGNED NOT NULL                 COMMENT '稳定随机推荐序键，发布时生成',
    view_count      INT             NOT NULL DEFAULT 0       COMMENT '浏览次数',
    is_deleted      TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '软删除标记：0正常/1已删除',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    PRIMARY KEY (id),
    INDEX idx_item_market_latest (school_id, status, moderation_status, is_deleted, created_at DESC, id DESC),
    INDEX idx_item_market_feed (school_id, status, moderation_status, is_deleted, feed_key, id),
    INDEX idx_item_market_price (school_id, status, moderation_status, is_deleted, price, created_at DESC, id DESC),
    INDEX idx_item_category_latest (school_id, category_id, status, moderation_status, is_deleted, created_at DESC, id DESC),
    INDEX idx_item_type_latest (school_id, type, status, moderation_status, is_deleted, created_at DESC, id DESC),
    INDEX idx_item_publisher_created (publisher_id, created_at DESC, id DESC),
    CONSTRAINT fk_item_publisher  FOREIGN KEY (publisher_id) REFERENCES sys_user(id),
    CONSTRAINT fk_item_school     FOREIGN KEY (school_id)    REFERENCES school(id),
    CONSTRAINT fk_item_category   FOREIGN KEY (category_id)  REFERENCES category(id),
    CONSTRAINT chk_item_type CHECK (type IN ('SELL', 'BUY', 'SWAP', 'ERRAND')),
    CONSTRAINT chk_item_status CHECK (status IN ('ON_SALE', 'SOLD', 'OFF_SHELF')),
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
    filter_tag VARCHAR(50) DEFAULT NULL COMMENT '商品标签筛选',
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
-- -----------------------------------------------------------
CREATE TABLE trade_order (
    id              BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '订单ID',
    item_id         BIGINT          NOT NULL                 COMMENT '商品ID',
    buyer_id        BIGINT          NOT NULL                 COMMENT '买家ID',
    seller_id       BIGINT          NOT NULL                 COMMENT '卖家ID',
    price           DECIMAL(10,2)   NOT NULL                 COMMENT '成交价格',
    status          VARCHAR(20)     NOT NULL DEFAULT 'WAITING_MEET' COMMENT '状态：WAITING_MEET/COMPLETED/CANCELLED',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
    completed_at    DATETIME        DEFAULT NULL             COMMENT '完成时间（确认收货）',
    cancelled_at    DATETIME        DEFAULT NULL             COMMENT '取消时间',

    PRIMARY KEY (id),
    INDEX idx_order_buyer_created (buyer_id, created_at DESC, id DESC),
    INDEX idx_order_buyer_status_created (buyer_id, status, created_at DESC, id DESC),
    INDEX idx_order_seller_created (seller_id, created_at DESC, id DESC),
    INDEX idx_order_seller_status_created (seller_id, status, created_at DESC, id DESC),
    INDEX idx_order_status_completed_item (status, completed_at, item_id, price),
    INDEX idx_order_item_status (item_id, status, id),
    CONSTRAINT fk_order_item   FOREIGN KEY (item_id)   REFERENCES item(id),
    CONSTRAINT fk_order_buyer  FOREIGN KEY (buyer_id)  REFERENCES sys_user(id),
    CONSTRAINT fk_order_seller FOREIGN KEY (seller_id) REFERENCES sys_user(id),
    CONSTRAINT chk_order_status CHECK (status IN ('WAITING_MEET', 'COMPLETED', 'CANCELLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易订单表';


-- -----------------------------------------------------------
-- 2.6 item_reservation — 商品订单独占预留表
-- -----------------------------------------------------------
CREATE TABLE item_reservation (
    item_id         BIGINT      NOT NULL                 COMMENT '商品ID（主键保证同一商品仅一个进行中订单）',
    buyer_id        BIGINT      NOT NULL                 COMMENT '占用商品的买家ID',
    order_id        BIGINT      DEFAULT NULL             COMMENT '创建完成后的订单ID',
    created_at      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '预留时间',

    PRIMARY KEY (item_id),
    UNIQUE KEY uk_reservation_order (order_id),
    INDEX idx_reservation_buyer (buyer_id),
    CONSTRAINT fk_reservation_item  FOREIGN KEY (item_id)  REFERENCES item(id),
    CONSTRAINT fk_reservation_buyer FOREIGN KEY (buyer_id) REFERENCES sys_user(id),
    CONSTRAINT fk_reservation_order FOREIGN KEY (order_id) REFERENCES trade_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='进行中订单的商品独占预留表';


-- -----------------------------------------------------------
-- 2.6 wallet_log — 钱包资金变动流水表
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
    INDEX idx_wallet_user_created (user_id, created_at DESC, id DESC),
    INDEX idx_order (order_id),
    INDEX idx_wallet_type_created (type, created_at DESC, id DESC),
    CONSTRAINT fk_wallet_user  FOREIGN KEY (user_id)  REFERENCES sys_user(id),
    CONSTRAINT fk_wallet_order FOREIGN KEY (order_id) REFERENCES trade_order(id),
    CONSTRAINT chk_wallet_type CHECK (type IN ('RECHARGE', 'PAYMENT', 'REFUND', 'INCOME'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包资金变动流水表';


-- -----------------------------------------------------------
-- 2.7 chat_message — 聊天消息记录表
-- -----------------------------------------------------------
CREATE TABLE chat_message (
    id                BIGINT        NOT NULL AUTO_INCREMENT  COMMENT '消息ID',
    conversation_id   VARCHAR(50)   NOT NULL                 COMMENT '会话ID（格式：小ID_大ID 或 userID_admin）',
    sender_id         BIGINT        NOT NULL                 COMMENT '发送者ID',
    receiver_id       BIGINT        NOT NULL                 COMMENT '接收者ID',
    content           TEXT          NOT NULL                 COMMENT '消息内容',
    related_item_id   BIGINT        DEFAULT NULL             COMMENT '关联商品ID',
    is_read           TINYINT(1)    NOT NULL DEFAULT 0       COMMENT '是否已读：0未读/1已读',
    created_at        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',

    PRIMARY KEY (id),
    INDEX idx_conversation (conversation_id, created_at),
    INDEX idx_receiver_unread (receiver_id, is_read, created_at DESC, id DESC),
    INDEX idx_sender_created (sender_id, created_at DESC, id DESC),
    CONSTRAINT fk_chat_sender   FOREIGN KEY (sender_id)       REFERENCES sys_user(id),
    CONSTRAINT fk_chat_receiver FOREIGN KEY (receiver_id)     REFERENCES sys_user(id),
    CONSTRAINT fk_chat_item     FOREIGN KEY (related_item_id) REFERENCES item(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息记录表';


-- -----------------------------------------------------------
-- 2.9 violation_report — 内容审核记录表
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
    handled_at              DATETIME        DEFAULT NULL             COMMENT '处理时间',

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
-- 2.9 violation_log — 违规处罚日志表
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
-- 2.10 reputation_penalty — 独立信誉处罚记录表
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
-- 2.11 violation_appeal — 内容违规申诉表
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
    handled_at  DATETIME        DEFAULT NULL             COMMENT '复核时间',

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
-- 2.12 exp_log — 经验值变动记录表（模块一成长体系）
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
-- 2.13 trade_review — 交易评价表（模块一创新功能：信誉体系）
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
-- -----------------------------------------------------------
INSERT INTO school (name, code, email_domain) VALUES
('上海大学', 'SHU', '@shu.edu.cn'),
('东华大学', 'DHU', '@dhu.edu.cn');

-- -----------------------------------------------------------
-- 3.1 系统管理员（仅通过 /admin/login 进入后台，不具备普通用户功能；初始化后请立即改密）
-- -----------------------------------------------------------
INSERT INTO sys_user (student_id, password, nickname, school_id, role, status, level, exp, wallet_balance, security_question, security_answer)
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
    '$2a$10$or0s3jeC85J07b8HcY9wfOJDE0gegLcyYkjFLn0yr.BE8koej.A1K'  -- 密码 123456
);

-- -----------------------------------------------------------
-- 3.2 预设商品大类（8个）
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
