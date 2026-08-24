package com.zhiyi.module.trade.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资金操作幂等记录。唯一键 (user_id, operation, idempotency_key) 永久占用；
 * owner_token 协议判定执行所有权，不依赖驱动 affected rows 语义。
 */
@Data
@TableName("idempotency_record")
public class IdempotencyRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** RECHARGE / ORDER_CREATE / ORDER_CONFIRM / ORDER_CANCEL */
    private String operation;
    private String idempotencyKey;
    /** 规范化完整请求参数的 SHA-256（hex，64 字符） */
    private String requestHash;
    /** 本次事务随机所有权令牌，不由客户端提供 */
    private String ownerToken;
    /** PROCESSING / SUCCESS（业务失败整体回滚，无 FAILED） */
    private String status;
    private String resultSnapshot;
    private Integer resultVersion;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
