package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.entity.ViolationAppeal;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationAppealMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.entity.ItemFavorite;
import com.zhiyi.module.item.entity.ItemViewStat;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemFavoriteMapper;
import com.zhiyi.module.item.mapper.ItemViewStatMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品卡片批量装配器（P2：产出内部 {@link ItemSnapshot}）。
 * 每批数据按表执行固定次数查询，避免逐行回表；对外形状由快照投影为
 * ItemSummaryResponse / ItemDetailResponse / ItemCardVO。
 *
 * RESERVED（交易中）由 item.status 派生（item_reservation 已淘汰）；
 * 浏览量来自独立的 item_view_stat 统计表。
 */
@Service
@RequiredArgsConstructor
public class ItemCardAssembler {

    private final CategoryMapper categoryMapper;
    private final SysUserMapper userMapper;
    private final ItemFavoriteMapper favoriteMapper;
    private final ItemViewStatMapper statMapper;
    private final ViolationReportMapper violationReportMapper;
    private final ViolationAppealMapper appealMapper;
    private final ItemTagService itemTagService;

    @Value("${zhiyi.moderation.appeal-window-days:7}")
    private int appealWindowDays = 7;

    public List<ItemSnapshot> assemble(List<Item> items, Long currentUserId) {
        if (items == null || items.isEmpty()) return List.of();

        Set<Long> categoryIds = items.stream().map(Item::getCategoryId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> publisherIds = items.stream().map(Item::getPublisherId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> itemIds = items.stream().map(Item::getId)
                .filter(Objects::nonNull).collect(Collectors.toCollection(HashSet::new));

        Map<Long, Category> categories = selectCategoryMap(categoryIds);
        Map<Long, SysUser> users = selectUserMap(publisherIds);
        Map<Long, List<String>> tags = itemTagService.tagsByItemIds(itemIds);
        Map<Long, Long> favoriteCounts = favoriteCounts(itemIds);
        Map<Long, Long> viewCounts = viewCounts(itemIds);
        Set<Long> myFavorites = currentUserId == null
                ? Collections.emptySet()
                : favoriteItemIds(currentUserId, itemIds);
        SysUser viewer = currentUserId == null ? null : userMapper.selectById(currentUserId);
        Map<Long, ViolationReport> latestViolations = latestConfirmedViolations(itemIds, currentUserId);
        Map<Long, ViolationAppeal> appealsByReport = appealsByReport(latestViolations.values());
        LocalDateTime now = LocalDateTime.now();

        return items.stream().map(item -> toSnapshot(item, currentUserId, viewer, now,
                categories, users, tags, favoriteCounts, viewCounts, myFavorites,
                latestViolations, appealsByReport)).toList();
    }

    private ItemSnapshot toSnapshot(Item item,
                                    Long currentUserId,
                                    SysUser viewer,
                                    LocalDateTime now,
                                    Map<Long, Category> categories,
                                    Map<Long, SysUser> users,
                                    Map<Long, List<String>> tags,
                                    Map<Long, Long> favoriteCounts,
                                    Map<Long, Long> viewCounts,
                                    Set<Long> myFavorites,
                                    Map<Long, ViolationReport> latestViolations,
                                    Map<Long, ViolationAppeal> appealsByReport) {
        Category category = categories.get(item.getCategoryId());
        SysUser publisher = users.get(item.getPublisherId());
        ItemSnapshot vo = new ItemSnapshot();
        vo.setId(item.getId());
        vo.setPublisherId(item.getPublisherId());
        if (publisher != null) {
            vo.setPublisherNickname(publisher.getNickname());
            vo.setPublisherAvatar(publisher.getAvatar());
            vo.setPublisherLevel(publisher.getLevel());
            vo.setPublisherLevelTitle(LevelRule.titleOf(publisher.getLevel()));
            vo.setPublisherVerified(StringUtils.hasText(publisher.getSchoolEmail()));
            vo.setDormitoryRelation(proximityRelation(viewer, publisher));
        }
        vo.setType(item.getType().code());
        vo.setTitle(item.getTitle());
        vo.setDescription(item.getDescription());
        vo.setCategoryId(item.getCategoryId());
        vo.setCategoryName(category == null ? null : category.getName());
        vo.setPrice(item.getPrice());
        List<String> images = item.getImages() == null ? List.of() : item.getImages();
        vo.setImages(images);
        // 无封面图统一序列化为显式 null（与 ChatItemSummaryVO/OrderVO 同一语义，不使用空字符串）
        vo.setCoverImage(images.isEmpty() ? null : images.getFirst());
        vo.setTags(tags.getOrDefault(item.getId(), List.of()));
        vo.setTradeLocation(item.getTradeLocation());
        vo.setPickupLocation(item.getPickupLocation());
        vo.setDeliveryLocation(item.getDeliveryLocation());
        vo.setStatus(item.getStatus().code());
        vo.setModerationStatus(item.getModerationStatus().code());
        // RESERVED（交易中）由商品状态派生：item.status 是可交易性唯一权威来源
        vo.setReserved(item.getStatus() == com.zhiyi.common.enums.ItemStatus.RESERVED);

        ViolationReport latestViolation = latestViolations.get(item.getId());
        ViolationAppeal appeal = latestViolation == null ? null : appealsByReport.get(latestViolation.getId());
        vo.setLatestViolationId(latestViolation == null ? null : latestViolation.getId());
        vo.setAppealStatus(appeal == null ? null : appeal.getStatus().code());
        boolean withinWindow = latestViolation != null && latestViolation.getHandledAt() != null
                && !now.isAfter(latestViolation.getHandledAt().plusDays(Math.max(1, appealWindowDays)));
        vo.setAppealable(Objects.equals(currentUserId, item.getPublisherId())
                && latestViolation != null && appeal == null && withinWindow);
        // 浏览量来自独立统计表（item 业务行不再承载浏览计数）；无统计行的商品按 0
        vo.setViewCount(viewCounts.getOrDefault(item.getId(), 0L));
        vo.setFavoriteCount(favoriteCounts.getOrDefault(item.getId(), 0L));
        vo.setFavoriteByCurrentUser(myFavorites.contains(item.getId()));
        vo.setCreatedAt(item.getCreatedAt());
        vo.setUpdatedAt(item.getUpdatedAt());
        return vo;
    }

    private Map<Long, ViolationReport> latestConfirmedViolations(Set<Long> itemIds, Long currentUserId) {
        if (currentUserId == null || itemIds.isEmpty()) return Map.of();
        List<ViolationReport> reports = violationReportMapper.selectList(
                new LambdaQueryWrapper<ViolationReport>()
                        .in(ViolationReport::getItemId, itemIds)
                        .eq(ViolationReport::getUserId, currentUserId)
                        .eq(ViolationReport::getStatus, ViolationStatus.CONFIRMED)
                        .orderByDesc(ViolationReport::getHandledAt)
                        .orderByDesc(ViolationReport::getId));
        Map<Long, ViolationReport> latest = new HashMap<>();
        reports.forEach(report -> latest.putIfAbsent(report.getItemId(), report));
        return latest;
    }

    private Map<Long, ViolationAppeal> appealsByReport(Collection<ViolationReport> reports) {
        Set<Long> reportIds = reports.stream().map(ViolationReport::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        if (reportIds.isEmpty()) return Map.of();
        return appealMapper.selectList(new LambdaQueryWrapper<ViolationAppeal>()
                        .in(ViolationAppeal::getReportId, reportIds))
                .stream().collect(Collectors.toMap(ViolationAppeal::getReportId, Function.identity()));
    }

    private Map<Long, Category> selectCategoryMap(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return categoryMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private Map<Long, SysUser> selectUserMap(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectByIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private Map<Long, Long> favoriteCounts(Set<Long> itemIds) {
        List<Map<String, Object>> rows = favoriteMapper.selectMaps(new QueryWrapper<ItemFavorite>()
                .select("item_id", "COUNT(*) AS favorite_count")
                .in("item_id", itemIds)
                .groupBy("item_id"));
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            result.put(toLong(row.get("item_id")), toLong(row.get("favorite_count")));
        }
        return result;
    }

    private Map<Long, Long> viewCounts(Set<Long> itemIds) {
        if (itemIds.isEmpty()) return Map.of();
        return statMapper.selectByIds(itemIds).stream()
                .collect(Collectors.toMap(ItemViewStat::getItemId, ItemViewStat::getViewCount));
    }

    private Set<Long> favoriteItemIds(Long userId, Set<Long> itemIds) {
        return favoriteMapper.selectList(new LambdaQueryWrapper<ItemFavorite>()
                        .eq(ItemFavorite::getUserId, userId)
                        .in(ItemFavorite::getItemId, itemIds))
                .stream().map(ItemFavorite::getItemId).collect(Collectors.toSet());
    }

    private String proximityRelation(SysUser viewer, SysUser publisher) {
        if (viewer == null || publisher == null
                || !Objects.equals(viewer.getSchoolId(), publisher.getSchoolId())) return null;
        String viewerDormitory = normalized(viewer.getDormitory());
        String publisherDormitory = normalized(publisher.getDormitory());
        String viewerCampus = normalized(viewer.getCampus());
        String publisherCampus = normalized(publisher.getCampus());
        boolean campusMatches = !viewerCampus.isEmpty() && viewerCampus.equals(publisherCampus);
        boolean campusConflicts = !viewerCampus.isEmpty() && !publisherCampus.isEmpty() && !campusMatches;
        if (!viewerDormitory.isEmpty() && viewerDormitory.equals(publisherDormitory) && !campusConflicts) {
            return "SAME_BUILDING";
        }
        return campusMatches ? "SAME_CAMPUS" : null;
    }

    private String normalized(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").trim();
    }

    private long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }
}
