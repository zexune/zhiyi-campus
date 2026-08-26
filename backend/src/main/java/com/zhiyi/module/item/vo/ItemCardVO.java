package com.zhiyi.module.item.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品大厅 / 搜索 / 收藏 / 榜单统一展示对象。
 *
 * 仅大厅核心五字段标注 REQUIRED；详情字段（描述、分类名、地点等）与
 * 用户相关字段（收藏态、申诉态）随视图填充，保持可选语义。
 */
@Data
public class ItemCardVO {
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
    private String description;
    private Long categoryId;
    private String categoryName;
    /** SELL/BUY/ERRAND 必有金额；SWAP（以物换物）恒为显式 null，前端按 (type, price) 渲染 */
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
    /** 当前用户是否还能对最近一次确认违规提交申诉。 */
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
