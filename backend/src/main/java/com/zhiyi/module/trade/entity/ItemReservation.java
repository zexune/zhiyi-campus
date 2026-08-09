package com.zhiyi.module.trade.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订单对商品的独占预留。item_id 为主键，从数据库层阻止并发重复下单。
 */
@Data
@TableName("item_reservation")
public class ItemReservation {
    @TableId(type = IdType.INPUT)
    private Long itemId;
    private Long buyerId;
    private Long orderId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
