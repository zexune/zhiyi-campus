package com.zhiyi.module.trade.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 交易评价（A6/A7）—— 买家确认收货后对卖家的一单一评。
 */
@Data
@TableName("trade_review")
public class TradeReview {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long orderId;       // 订单ID（一单一评，唯一）
    private Long reviewerId;    // 评价者（买家）
    private Long targetId;      // 被评价者（卖家）
    private Integer rating;     // 1-5 星
    private Boolean accurate;   // 描述是否准确
    private String comment;     // 评价内容（可选）

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
