package com.zhiyi.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.enums.WalletLogType;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("wallet_log")
public class WalletLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private WalletLogType type;
    private BigDecimal amount;
    private BigDecimal balanceAfter;
    private Long orderId;
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
