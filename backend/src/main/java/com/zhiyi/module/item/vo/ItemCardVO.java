package com.zhiyi.module.item.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品大厅 / 搜索 / 收藏 / 榜单统一展示对象。
 */
@Data
public class ItemCardVO {
    private Long id;
    private Long publisherId;
    private String publisherNickname;
    private Integer publisherLevel;
    private String publisherLevelTitle;
    private Boolean publisherVerified;
    /** SAME_BUILDING / SAME_CAMPUS / null */
    private String dormitoryRelation;
    private String type;
    private String title;
    private String description;
    private Long categoryId;
    private String categoryName;
    private BigDecimal price;
    private List<String> images;
    private String coverImage;
    private List<String> tags;
    private String tradeLocation;
    private String pickupLocation;
    private String deliveryLocation;
    private String status;
    private String moderationStatus;
    /** 是否存在进行中的订单预占；它不是商品状态。 */
    private Boolean reserved;
    /** 当前用户是否还能对最近一次确认违规提交申诉。 */
    private Boolean appealable;
    private String appealStatus;
    private Long latestViolationId;
    private Integer viewCount;
    private Long favoriteCount;
    private Boolean favoriteByCurrentUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
