package com.zhiyi.module.item.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品详情响应（P2 语义拆分）：/item/{id}、/item/my-items/{id}、我的发布列表族专用。
 *
 * 完整快照视图：描述、分类、三个地点、审核状态、申诉入口全部可见；
 * 申诉族字段（appealable/appealStatus/latestViolationId）仅对发布者填充。
 */
@Data
public class ItemDetailResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    private Long publisherId;
    private String publisherNickname;
    /** 发布者自定义头像；未上传时序列化为显式 null（前端回退文字头像） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String publisherAvatar;
    private Integer publisherLevel;
    private String publisherLevelTitle;
    private Boolean publisherVerified;
    /** SAME_BUILDING / SAME_CAMPUS / null */
    private String dormitoryRelation;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String type;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    private String description;
    private Long categoryId;
    private String categoryName;
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
    private String tradeLocation;
    private String pickupLocation;
    private String deliveryLocation;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    private String moderationStatus;
    /** 是否存在进行中的订单预占；它不是商品状态。 */
    private Boolean reserved;
    /** 当前用户是否还能对最近一次确认违规提交申诉（仅发布者视角有值）。 */
    private Boolean appealable;
    private String appealStatus;
    private Long latestViolationId;
    /** 浏览量（独立统计表派生，只增不减） */
    private Long viewCount;
    private Long favoriteCount;
    private Boolean favoriteByCurrentUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
