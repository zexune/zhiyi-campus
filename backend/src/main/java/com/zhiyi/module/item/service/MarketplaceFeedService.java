package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 大厅查询策略：稳定随机键 + 分层范围查询，完全移除 ORDER BY RAND() 和校内用户全量扫描。
 */
@Service
@RequiredArgsConstructor
public class MarketplaceFeedService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ItemMapper itemMapper;

    public IPage<Item> list(Criteria criteria, SysUser viewer, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        String sort = normalizeSort(criteria.sort());
        if (!"random".equals(sort)) {
            return itemMapper.selectPage(new Page<>(safePage, safeSize),
                    buildWrapper(criteria, viewer.getSchoolId(), FeedTier.ALL, viewer, sort));
        }

        List<FeedTier> tiers = tiersFor(viewer);
        if (tiers.size() == 1) {
            return itemMapper.selectPage(new Page<>(safePage, safeSize),
                    buildWrapper(criteria, viewer.getSchoolId(), tiers.getFirst(), viewer, sort));
        }
        return pagedTieredFeed(criteria, viewer, safePage, safeSize, tiers);
    }

    private IPage<Item> pagedTieredFeed(Criteria criteria,
                                        SysUser viewer,
                                        int page,
                                        int size,
                                        List<FeedTier> tiers) {
        List<TierCount> counts = tiers.stream()
                .map(tier -> new TierCount(tier, itemMapper.selectCount(
                        buildWrapper(criteria, viewer.getSchoolId(), tier, viewer, "count"))))
                .toList();
        long total = counts.stream().mapToLong(TierCount::count).sum();
        long offset = (long) (page - 1) * size;
        int remaining = size;
        List<Item> records = new ArrayList<>(size);

        for (TierCount tierCount : counts) {
            if (remaining == 0) break;
            if (offset >= tierCount.count()) {
                offset -= tierCount.count();
                continue;
            }
            long tierOffset = offset;
            offset = 0;
            LambdaQueryWrapper<Item> wrapper = buildWrapper(
                    criteria, viewer.getSchoolId(), tierCount.tier(), viewer, "random");
            wrapper.last("LIMIT " + tierOffset + ", " + remaining);
            List<Item> slice = itemMapper.selectList(wrapper);
            records.addAll(slice);
            remaining -= slice.size();
        }

        Page<Item> result = new Page<>(page, size, total);
        result.setRecords(records);
        return result;
    }

    private LambdaQueryWrapper<Item> buildWrapper(Criteria criteria,
                                                   Long schoolId,
                                                   FeedTier tier,
                                                   SysUser viewer,
                                                   String sort) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<Item>()
                .eq(Item::getSchoolId, schoolId)
                .eq(Item::getStatus, ItemStatus.ON_SALE)
                .eq(Item::getModerationStatus, ModerationStatus.PASSED)
                .eq(Item::getIsDeleted, false)
                .notExists("SELECT 1 FROM item_reservation r WHERE r.item_id = item.id");

        if (StringUtils.hasText(criteria.keyword())) {
            String keyword = criteria.keyword().trim();
            wrapper.and(nested -> nested.like(Item::getTitle, keyword)
                    .or().like(Item::getDescription, keyword)
                    .or().apply("EXISTS (SELECT 1 FROM item_tag ik JOIN tag tk ON tk.id = ik.tag_id "
                            + "WHERE ik.item_id = item.id AND tk.name LIKE CONCAT('%', {0}, '%'))", keyword));
        }
        wrapper.eq(criteria.categoryId() != null, Item::getCategoryId, criteria.categoryId());
        wrapper.ge(criteria.minPrice() != null, Item::getPrice, criteria.minPrice());
        wrapper.le(criteria.maxPrice() != null, Item::getPrice, criteria.maxPrice());
        if (StringUtils.hasText(criteria.type())) {
            try {
                wrapper.eq(Item::getType, ItemType.from(criteria.type()));
            } catch (IllegalArgumentException invalidType) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "商品类型不合法");
            }
        }
        if (StringUtils.hasText(criteria.tag())) {
            String normalized = criteria.tag().trim().toLowerCase(Locale.ROOT);
            wrapper.apply("EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id "
                    + "WHERE it.item_id = item.id AND t.normalized_name = {0})", normalized);
        }
        applyTier(wrapper, tier, viewer);
        applySort(wrapper, sort);
        return wrapper;
    }

    private void applyTier(LambdaQueryWrapper<Item> wrapper, FeedTier tier, SysUser viewer) {
        String campusKey = locationKey(viewer.getCampus());
        String dormitoryKey = locationKey(viewer.getDormitory());
        switch (tier) {
            case SAME_BUILDING -> {
                if (!campusKey.isEmpty()) {
                    wrapper.apply("publisher_id IN (SELECT u.id FROM sys_user u "
                                    + "WHERE u.school_id = {0} AND u.campus_key = {1} AND u.dormitory_key = {2})",
                            viewer.getSchoolId(), campusKey, dormitoryKey);
                } else {
                    wrapper.apply("publisher_id IN (SELECT u.id FROM sys_user u "
                                    + "WHERE u.school_id = {0} AND u.dormitory_key = {1})",
                            viewer.getSchoolId(), dormitoryKey);
                }
            }
            case SAME_CAMPUS -> {
                wrapper.apply("publisher_id IN (SELECT u.id FROM sys_user u "
                                + "WHERE u.school_id = {0} AND u.campus_key = {1})",
                        viewer.getSchoolId(), campusKey);
                if (!dormitoryKey.isEmpty()) {
                    wrapper.apply("publisher_id NOT IN (SELECT u.id FROM sys_user u "
                                    + "WHERE u.school_id = {0} AND u.campus_key = {1} AND u.dormitory_key = {2})",
                            viewer.getSchoolId(), campusKey, dormitoryKey);
                }
            }
            case OTHERS -> {
                if (!campusKey.isEmpty()) {
                    wrapper.apply("publisher_id NOT IN (SELECT u.id FROM sys_user u "
                                    + "WHERE u.school_id = {0} AND u.campus_key = {1})",
                            viewer.getSchoolId(), campusKey);
                } else {
                    wrapper.apply("publisher_id NOT IN (SELECT u.id FROM sys_user u "
                                    + "WHERE u.school_id = {0} AND u.dormitory_key = {1})",
                            viewer.getSchoolId(), dormitoryKey);
                }
            }
            case ALL -> { }
        }
    }

    private void applySort(LambdaQueryWrapper<Item> wrapper, String sort) {
        switch (sort) {
            case "priceAsc" -> wrapper.orderByAsc(Item::getPrice)
                    .orderByDesc(Item::getCreatedAt).orderByDesc(Item::getId);
            case "priceDesc" -> wrapper.orderByDesc(Item::getPrice)
                    .orderByDesc(Item::getCreatedAt).orderByDesc(Item::getId);
            case "latest" -> wrapper.orderByDesc(Item::getCreatedAt).orderByDesc(Item::getId);
            case "views" -> wrapper.orderByDesc(Item::getViewCount)
                    .orderByDesc(Item::getCreatedAt).orderByDesc(Item::getId);
            case "random" -> wrapper.orderByAsc(Item::getFeedKey).orderByAsc(Item::getId);
            case "count" -> { }
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "排序方式不合法");
        }
    }

    private List<FeedTier> tiersFor(SysUser viewer) {
        boolean hasCampus = StringUtils.hasText(viewer.getCampus());
        boolean hasDormitory = StringUtils.hasText(viewer.getDormitory());
        if (hasCampus && hasDormitory) {
            return List.of(FeedTier.SAME_BUILDING, FeedTier.SAME_CAMPUS, FeedTier.OTHERS);
        }
        if (hasCampus) return List.of(FeedTier.SAME_CAMPUS, FeedTier.OTHERS);
        if (hasDormitory) return List.of(FeedTier.SAME_BUILDING, FeedTier.OTHERS);
        return List.of(FeedTier.ALL);
    }

    private String normalizeSort(String sort) {
        return StringUtils.hasText(sort) ? sort.trim() : "random";
    }

    private String locationKey(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    public record Criteria(String keyword,
                           Long categoryId,
                           BigDecimal minPrice,
                           BigDecimal maxPrice,
                           String sort,
                           String type,
                           String tag) {
    }

    private record TierCount(FeedTier tier, long count) {
    }

    private enum FeedTier {
        SAME_BUILDING, SAME_CAMPUS, OTHERS, ALL
    }
}
