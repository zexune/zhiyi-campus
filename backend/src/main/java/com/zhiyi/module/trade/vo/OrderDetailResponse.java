package com.zhiyi.module.trade.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单订单操作响应（P2 拆分目标之一）：下单 / 确认收货 / 取消的写接口返回体。
 *
 * 与列表行 {@link OrderVO}（即 OrderListRowResponse 目标，暂保留原名避免机械复制）
 * 的真实差异：单订单操作语义上不存在"买家是否已评价"——reviewed 只属于
 * 「我买的」列表行（A7 评价入口显隐），因此本响应不携带该字段。
 */
@Data
public class OrderDetailResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long buyerId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long sellerId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal price;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTitle;
    /** 无封面商品序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String itemCover;
    /** 对方昵称；系统主体场景可为 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String peerNickname;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
    /** 仅已完成订单存在 */
    private LocalDateTime completedAt;
    /** 仅已取消订单存在 */
    private LocalDateTime cancelledAt;

    public static OrderDetailResponse from(OrderVO vo) {
        OrderDetailResponse response = new OrderDetailResponse();
        response.setId(vo.getId());
        response.setItemId(vo.getItemId());
        response.setBuyerId(vo.getBuyerId());
        response.setSellerId(vo.getSellerId());
        response.setPrice(vo.getPrice());
        response.setStatus(vo.getStatus());
        response.setItemTitle(vo.getItemTitle());
        response.setItemCover(vo.getItemCover());
        response.setPeerNickname(vo.getPeerNickname());
        response.setCreatedAt(vo.getCreatedAt());
        response.setCompletedAt(vo.getCompletedAt());
        response.setCancelledAt(vo.getCancelledAt());
        return response;
    }
}
