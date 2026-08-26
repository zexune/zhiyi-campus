package com.zhiyi.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.enums.WalletLogType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wallet_log")
public class WalletLog {
    @TableId(type = IdType.AUTO)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    private Long userId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private WalletLogType type;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal balanceAfter;
    /** 非交易流水（如充值）无关联订单 */
    private Long orderId;
    /** 操作备注，可能为空 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
}
