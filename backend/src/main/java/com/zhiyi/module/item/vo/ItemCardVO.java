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
    private List<String> aiTags;
    private String tradeLocation;
    private String pickupLocation;
    private String deliveryLocation;
    private LocalDateTime deadlineTime;
    /** >7天不展示，3-7天展示闹钟，3天内展示警告。 */
    private String deadlineLabel;
    private String status;
    private Integer viewCount;
    private Long favoriteCount;
    private Boolean favoriteByCurrentUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
