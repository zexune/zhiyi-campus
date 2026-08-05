package com.zhiyi.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wallet_log")
public class WalletLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String type;            // RECHARGE / PAYMENT / REFUND / INCOME
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Long orderId;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
