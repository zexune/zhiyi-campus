package com.zhiyi.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.enums.OrderCancelReason;
import com.zhiyi.common.enums.OrderStatus;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("trade_order")
public class TradeOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itemId;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal price;
    private OrderStatus status;
    /** 取消原因：USER_CANCEL/AUTO_CANCEL/ADMIN_FORCE；非取消状态必须为 NULL。 */
    private OrderCancelReason cancelReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;
}
