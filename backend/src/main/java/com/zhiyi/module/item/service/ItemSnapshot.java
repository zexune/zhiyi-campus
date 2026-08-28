package com.zhiyi.module.item.service;

import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.item.vo.ItemDetailResponse;
import com.zhiyi.module.item.vo.ItemSummaryResponse;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品内部快照（P2）：装配器批量填充的唯一中间表示。
 *
 * 禁止直接出现在 Controller 返回类型 / OpenAPI 边界——对外形状必须经
 * toSummary()/toDetail()/toCard() 投影为语义响应；toCard() 是发布/编辑/
 * 重新上架/收藏列表族的既定响应形状（与 toDetail() 仅差 publisherAvatar），
 * 属长期共存的项目决定，不是待迁移的兼容层。
 */
@Data
public class ItemSnapshot {

    private Long id;
    private Long publisherId;
    private String publisherNickname;
    private String publisherAvatar;
    private Integer publisherLevel;
    private String publisherLevelTitle;
    private Boolean publisherVerified;
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
    private Boolean reserved;
    private Boolean appealable;
    private String appealStatus;
    private Long latestViolationId;
    private Long viewCount;
    private Long favoriteCount;
    private Boolean favoriteByCurrentUser;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 卡片投影（发布/编辑/重新上架/收藏列表族）：与 toDetail() 仅差 publisherAvatar。 */
    public ItemCardVO toCard() {
        ItemCardVO vo = new ItemCardVO();
        vo.setId(id);
        vo.setPublisherId(publisherId);
        vo.setPublisherNickname(publisherNickname);
        vo.setPublisherLevel(publisherLevel);
        vo.setPublisherLevelTitle(publisherLevelTitle);
        vo.setPublisherVerified(publisherVerified);
        vo.setDormitoryRelation(dormitoryRelation);
        vo.setType(type);
        vo.setTitle(title);
        vo.setDescription(description);
        vo.setCategoryId(categoryId);
        vo.setCategoryName(categoryName);
        vo.setPrice(price);
        vo.setImages(images);
        vo.setCoverImage(coverImage);
        vo.setTags(tags);
        vo.setTradeLocation(tradeLocation);
        vo.setPickupLocation(pickupLocation);
        vo.setDeliveryLocation(deliveryLocation);
        vo.setStatus(status);
        vo.setModerationStatus(moderationStatus);
        vo.setReserved(reserved);
        vo.setAppealable(appealable);
        vo.setAppealStatus(appealStatus);
        vo.setLatestViolationId(latestViolationId);
        vo.setViewCount(viewCount);
        vo.setFavoriteCount(favoriteCount);
        vo.setFavoriteByCurrentUser(favoriteByCurrentUser);
        vo.setCreatedAt(createdAt);
        vo.setUpdatedAt(updatedAt);
        return vo;
    }

    /** 摘要投影：feed/榜单/swap/errand 族。 */
    public ItemSummaryResponse toSummary() {
        ItemSummaryResponse vo = new ItemSummaryResponse();
        vo.setId(id);
        vo.setPublisherId(publisherId);
        vo.setPublisherNickname(publisherNickname);
        vo.setPublisherLevel(publisherLevel);
        vo.setPublisherLevelTitle(publisherLevelTitle);
        vo.setPublisherVerified(publisherVerified);
        vo.setDormitoryRelation(dormitoryRelation);
        vo.setType(type);
        vo.setTitle(title);
        vo.setPrice(price);
        vo.setImages(images);
        vo.setCoverImage(coverImage);
        vo.setTags(tags);
        vo.setStatus(status);
        vo.setReserved(reserved);
        vo.setViewCount(viewCount);
        vo.setFavoriteCount(favoriteCount);
        vo.setFavoriteByCurrentUser(favoriteByCurrentUser);
        vo.setCreatedAt(createdAt);
        return vo;
    }

    /** 详情投影：详情/我的发布族。 */
    public ItemDetailResponse toDetail() {
        ItemDetailResponse vo = new ItemDetailResponse();
        vo.setId(id);
        vo.setPublisherId(publisherId);
        vo.setPublisherNickname(publisherNickname);
        vo.setPublisherAvatar(publisherAvatar);
        vo.setPublisherLevel(publisherLevel);
        vo.setPublisherLevelTitle(publisherLevelTitle);
        vo.setPublisherVerified(publisherVerified);
        vo.setDormitoryRelation(dormitoryRelation);
        vo.setType(type);
        vo.setTitle(title);
        vo.setDescription(description);
        vo.setCategoryId(categoryId);
        vo.setCategoryName(categoryName);
        vo.setPrice(price);
        vo.setImages(images);
        vo.setCoverImage(coverImage);
        vo.setTags(tags);
        vo.setTradeLocation(tradeLocation);
        vo.setPickupLocation(pickupLocation);
        vo.setDeliveryLocation(deliveryLocation);
        vo.setStatus(status);
        vo.setModerationStatus(moderationStatus);
        vo.setReserved(reserved);
        vo.setAppealable(appealable);
        vo.setAppealStatus(appealStatus);
        vo.setLatestViolationId(latestViolationId);
        vo.setViewCount(viewCount);
        vo.setFavoriteCount(favoriteCount);
        vo.setFavoriteByCurrentUser(favoriteByCurrentUser);
        vo.setCreatedAt(createdAt);
        vo.setUpdatedAt(updatedAt);
        return vo;
    }
}
