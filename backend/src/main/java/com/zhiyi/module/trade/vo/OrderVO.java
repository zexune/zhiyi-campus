package com.zhiyi.module.trade.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易订单返回体（列表 & 详情共用）
 */
@Data
public class OrderVO {
    private Long id;
    private Long itemId;
    private Long buyerId;
    private Long sellerId;
    private BigDecimal price;
    private String status;          // WAITING_MEET / COMPLETED / CANCELLED

    /** 商品快照（下单时记录，不随后续编辑变化） */
    private String itemTitle;
    private String itemCover;       // 首张图片

    /** 对方昵称（买家视角=卖家昵称，卖家视角=买家昵称） */
    private String peerNickname;

    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
    private LocalDateTime cancelledAt;

    /** 买家是否已对本单评价（A7，仅「我买的」列表填充；控制评价入口显隐） */
    private Boolean reviewed;
}
