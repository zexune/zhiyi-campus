package com.zhiyi.module.item.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品摘要响应（P2 语义拆分）：feed 游标、榜单、swap/errand 列表族专用。
 *
 * 与 {@link ItemCardVO}（兼容适配层）的字段差异是刻意的：摘要族不暴露
 * 描述、分类、地点、审核与申诉等详情/所有者字段——按 endpoint 家族的
 * 字段可见性拆分，不机械复制整卡 JSON。
 */
@Data
public class ItemSummaryResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    private Long publisherId;
    private String publisherNickname;
    private Integer publisherLevel;
    private String publisherLevelTitle;
    private Boolean publisherVerified;
    /** SAME_BUILDING / SAME_CAMPUS / null */
    private String dormitoryRelation;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    /** SELL/BUY/ERRAND 必有金额；SWAP（以物换物）恒为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private BigDecimal price;
    private List<String> images;
    /** 无封面图时序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String coverImage;
    private List<String> tags;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    /** 是否存在进行中的订单预占；它不是商品状态。 */
    private Boolean reserved;
    /** 浏览量（独立统计表派生，只增不减） */
    private Long viewCount;
    private Long favoriteCount;
    private Boolean favoriteByCurrentUser;
    private LocalDateTime createdAt;
}
