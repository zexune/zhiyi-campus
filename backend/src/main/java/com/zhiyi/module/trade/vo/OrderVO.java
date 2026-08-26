package com.zhiyi.module.trade.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 交易订单返回体（列表 & 详情共用）
 *
 * 字段通过 @Schema(requiredMode) 声明 nullability 契约：springdoc 据此输出
 * required/nullable，前端 openapi-typescript 才能生成非可选字段与 `| null` 联合。
 * 新增字段必须同步标注；"不同视图条件填充"的字段保持不标注（可选语义）。
 */
@Data
public class OrderVO {
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
    private String status;          // WAITING_MEET / COMPLETED / CANCELLED

    /** 商品快照（下单时记录，不随后续编辑变化） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String itemTitle;

    /** 无封面商品序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String itemCover;       // 首张图片

    /** 对方昵称（买家视角=卖家昵称，卖家视角=买家昵称）；系统主体场景可为 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String peerNickname;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;

    /** 仅已完成订单存在；未完成时省略 */
    private LocalDateTime completedAt;

    /** 仅已取消订单存在；未取消时省略 */
    private LocalDateTime cancelledAt;

    /** 买家是否已对本单评价（A7，仅「我买的」列表填充；控制评价入口显隐） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean reviewed;
}
