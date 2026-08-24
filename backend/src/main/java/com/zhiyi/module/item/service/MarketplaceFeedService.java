package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.entity.ItemViewStat;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.mapper.ItemViewStatMapper;
import com.zhiyi.module.item.support.FeedCursorCodec;
import com.zhiyi.module.item.vo.FeedCursorState;
import com.zhiyi.module.item.vo.ViewsSortQuery;
import com.zhiyi.module.user.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 大厅查询策略：签名游标 + 稳定 keyset 分页（B9 根因修复）。
 *
 * - 随机 Feed 依赖发布时固化的 feed_key（同一 listing revision 内不可变），
 *   不再使用 COUNT + OFFSET 切片，跨页无重复、无深 OFFSET；
 * - 游标绑定规范化筛选哈希、用户 profile_version、快照上界（最大 item id 与
 *   listing_revision）与过期时间；筛选/资料版本变化、过期或签名不匹配要求从首屏重启；
 * - 页间发布的新商品被 id 上界排除；被编辑商品因 revision 超出上界退出旧链
 *   （可消失，不能以新位置再次出现）；已成交/下架商品合法消失并补足本页；
 * - 分层推荐使用商品发布时固化的 publisher_campus_key / publisher_dormitory_key，
 *   用户改资料不移动进行中的 Feed 链；
 * - total 仅为首屏估算值（estimatedTotal），不承诺跨页精确。
 */
@Service
@RequiredArgsConstructor
public class MarketplaceFeedService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ItemMapper itemMapper;
    private final ItemViewStatMapper statMapper;
    private final FeedCursorCodec cursorCodec;

    @Value("${zhiyi.feed.cursor-ttl-seconds:900}")
    private long cursorTtlSeconds;

    /** 游标式分页结果（records 由调用方装配 VO）。 */
    public record FeedPage(List<Item> records, String nextCursor, boolean hasMore, long estimatedTotal) {
    }

    public FeedPage listByCursor(Criteria criteria, SysUser viewer, String cursor, int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        String sort = normalizeSort(criteria.sort());
        String filterHash = filterHash(criteria, sort);

        FeedCursorState state;
        if (!StringUtils.hasText(cursor)) {
            state = issueNewState(criteria, viewer, sort, filterHash);
        } else {
            state = requireValidCursor(cursor, viewer, filterHash);
        }

        List<Item> records = new ArrayList<>(safeSize);
        boolean hasMore = false;
        int tierIndex = 0;
        Long nextBoundaryItemId = null;

        if ("views".equals(sort)) {
            Long cursorViewCount = null;
            if (state.lastItemId != null) {
                ItemViewStat stat = statMapper.selectById(state.lastItemId);
                cursorViewCount = stat == null ? null : stat.getViewCount();
            }
            List<Item> page = itemMapper.selectViewsSortedPage(ViewsSortQuery.builder()
                    .schoolId(viewer.getSchoolId())
                    .keyword(StringUtils.hasText(criteria.keyword()) ? criteria.keyword().trim() : null)
                    .categoryId(criteria.categoryId())
                    .minPrice(criteria.minPrice())
                    .maxPrice(criteria.maxPrice())
                    .type(normalizedType(criteria.type()))
                    .tags(normalizedTags(criteria.tags()))
                    .snapshotMaxItemId(state.snapshotMaxItemId)
                    .snapshotMaxRevision(state.snapshotMaxRevision)
                    .cursorViewCount(cursorViewCount)
                    .cursorItemId(state.lastItemId)
                    .limit(safeSize + 1)
                    .build());
            hasMore = page.size() > safeSize;
            if (hasMore) {
                page = page.subList(0, safeSize);
            }
            records.addAll(page);
            if (hasMore && !page.isEmpty()) {
                nextBoundaryItemId = page.getLast().getId();
            }
        } else {
            List<FeedTier> tiers = "random".equals(sort) ? tiersFor(viewer) : List.of(FeedTier.ALL);
            tierIndex = Math.min(state.tierIndex, tiers.size() - 1);
            int remaining = safeSize + 1;
            for (int index = tierIndex; index < tiers.size() && remaining > 0; index++) {
                boolean tierStart = (lastBoundaryOf(state) == null) || index != tierIndex;
                Long keysetSortKey = tierStart ? null : state.lastSortKey;
                Long keysetItemId = tierStart ? null : state.lastItemId;
                List<Item> slice = itemMapper.selectList(buildWrapper(
                        criteria, viewer, tiers.get(index), sort, keysetSortKey, keysetItemId,
                        state.snapshotMaxItemId, state.snapshotMaxRevision)
                        .last("LIMIT " + remaining));
                records.addAll(slice);
                remaining -= slice.size();
                tierIndex = index;
                if (remaining <= 0) {
                    hasMore = true;
                    break;
                }
            }
            if (records.size() > safeSize) {
                records = new ArrayList<>(records.subList(0, safeSize));
            }
            if (hasMore && !records.isEmpty()) {
                nextBoundaryItemId = records.getLast().getId();
            }
        }

        String nextCursor = null;
        if (hasMore && nextBoundaryItemId != null) {
            FeedCursorState next = copy(state);
            next.tierIndex = tierIndex;
            next.lastItemId = nextBoundaryItemId;
            Item last = records.getLast();
            switch (sort) {
                case "random" -> next.lastSortKey = last.getFeedKey();
                case "latest" -> next.lastSortKey = epochSecond(last.getCreatedAt());
                case "priceAsc", "priceDesc" -> next.lastSortKey = priceCents(last);
                case "views" -> next.lastSortKey = null;
                default -> throw new BusinessException(ResultCode.BAD_REQUEST, "排序方式不合法");
            }
            next.lastSecondaryKey = null;
            nextCursor = cursorCodec.encode(next);
        }
        return new FeedPage(records, nextCursor, hasMore, state.estimatedTotal);
    }

    // ================================================================
    // 游标签发与校验
    // ================================================================

    private FeedCursorState issueNewState(Criteria criteria, SysUser viewer, String sort, String filterHash) {
        long estimatedTotal = 0;
        if ("random".equals(sort)) {
            for (FeedTier tier : tiersFor(viewer)) {
                estimatedTotal += itemMapper.selectCount(buildWrapper(
                        criteria, viewer, tier, sort, null, null, Long.MAX_VALUE, Long.MAX_VALUE));
            }
        } else {
            estimatedTotal = itemMapper.selectCount(buildWrapper(
                    criteria, viewer, FeedTier.ALL, sort, null, null, Long.MAX_VALUE, Long.MAX_VALUE));
        }
        FeedCursorState state = new FeedCursorState();
        state.userId = viewer.getId() == null ? 0 : viewer.getId();
        state.filterHash = filterHash;
        state.profileVersion = viewer.getProfileVersion() == null ? 0 : viewer.getProfileVersion();
        state.snapshotMaxItemId = itemMapper.maxItemId();
        state.snapshotMaxRevision = itemMapper.currentListingRevision();
        state.sort = sort;
        state.tierIndex = 0;
        state.estimatedTotal = estimatedTotal;
        state.expiresAtEpochSecond = Instant.now().getEpochSecond() + cursorTtlSeconds;
        return state;
    }

    private FeedCursorState requireValidCursor(String cursor, SysUser viewer, String filterHash) {
        FeedCursorState state = cursorCodec.decode(cursor);
        if (state == null || state.expiresAtEpochSecond <= Instant.now().getEpochSecond()) {
            throw new BusinessException(ResultCode.FEED_CURSOR_INVALID);
        }
        // 游标链只能由签发者本人续页：跨用户/换号复用一律从首屏重启
        if (state.userId != (viewer.getId() == null ? 0 : viewer.getId())) {
            throw new BusinessException(ResultCode.FEED_CURSOR_INVALID);
        }
        if (!filterHash.equals(state.filterHash)) {
            throw new BusinessException(ResultCode.FEED_CURSOR_INVALID);
        }
        long currentProfileVersion = viewer.getProfileVersion() == null ? 0 : viewer.getProfileVersion();
        if (currentProfileVersion != state.profileVersion) {
            throw new BusinessException(ResultCode.FEED_CURSOR_INVALID);
        }
        return state;
    }

    private Long lastBoundaryOf(FeedCursorState state) {
        return state.lastItemId;
    }

    // ================================================================
    // 查询构造
    // ================================================================

    private LambdaQueryWrapper<Item> buildWrapper(Criteria criteria, SysUser viewer, FeedTier tier,
                                                  String sort, Long lastSortKey, Long lastItemId,
                                                  long snapshotMaxItemId, long snapshotMaxRevision) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<Item>()
                .eq(Item::getSchoolId, viewer.getSchoolId())
                .eq(Item::getStatus, ItemStatus.ON_SALE)
                .eq(Item::getModerationStatus, ModerationStatus.PASSED)
                .eq(Item::getIsDeleted, false)
                .le(Item::getId, snapshotMaxItemId)
                .le(Item::getListingRevision, snapshotMaxRevision);

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
        String type = normalizedType(criteria.type());
        if (type != null) {
            wrapper.eq(Item::getType, ItemType.from(type));
        }
        List<String> normalized = normalizedTags(criteria.tags());
        if (!normalized.isEmpty()) {
            StringBuilder placeholders = new StringBuilder();
            for (int i = 0; i < normalized.size(); i++) {
                if (i > 0) placeholders.append(',');
                placeholders.append('{').append(i).append('}');
            }
            wrapper.apply("EXISTS (SELECT 1 FROM item_tag it JOIN tag t ON t.id = it.tag_id "
                    + "WHERE it.item_id = item.id AND t.normalized_name IN ("
                    + placeholders + "))", normalized.toArray());
        }

        applyTierFilter(wrapper, tier, viewer);

        // keyset 边界（排序键 + 唯一 id 决定论顺序；快照内排序键不可变）
        if (lastSortKey != null && lastItemId != null) {
            switch (sort) {
                case "random" -> wrapper.apply(
                        "(feed_key > {0} OR (feed_key = {0} AND id > {1}))", lastSortKey, lastItemId);
                case "latest" -> wrapper.apply(
                        "(created_at < {0} OR (created_at = {0} AND id < {1}))",
                        fromEpochSecond(lastSortKey), lastItemId);
                case "priceAsc" -> wrapper.apply(
                        "(price * 100 > {0} OR (price * 100 = {0} AND id > {1}))", lastSortKey, lastItemId);
                case "priceDesc" -> wrapper.apply(
                        "(price * 100 < {0} OR (price * 100 = {0} AND id < {1}))", lastSortKey, lastItemId);
                default -> throw new BusinessException(ResultCode.BAD_REQUEST, "排序方式不合法");
            }
        }

        switch (sort) {
            case "priceAsc" -> wrapper.orderByAsc(Item::getPrice).orderByAsc(Item::getId);
            case "priceDesc" -> wrapper.orderByDesc(Item::getPrice).orderByDesc(Item::getId);
            case "latest" -> wrapper.orderByDesc(Item::getCreatedAt).orderByDesc(Item::getId);
            case "random" -> wrapper.orderByAsc(Item::getFeedKey).orderByAsc(Item::getId);
            default -> { }
        }
        return wrapper;
    }

    private void applyTierFilter(LambdaQueryWrapper<Item> wrapper, FeedTier tier, SysUser viewer) {
        String campusKey = locationKey(viewer.getCampus());
        String dormitoryKey = locationKey(viewer.getDormitory());
        switch (tier) {
            case SAME_BUILDING -> {
                if (dormitoryKey == null) {
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.eq(Item::getPublisherDormitoryKey, dormitoryKey);
                if (campusKey != null) {
                    wrapper.eq(Item::getPublisherCampusKey, campusKey);
                }
            }
            case SAME_CAMPUS -> {
                if (campusKey == null) {
                    wrapper.apply("1 = 0");
                    return;
                }
                wrapper.eq(Item::getPublisherCampusKey, campusKey);
                if (dormitoryKey != null) {
                    wrapper.ne(Item::getPublisherDormitoryKey, dormitoryKey);
                }
            }
            case OTHERS -> {
                if (campusKey != null) {
                    wrapper.and(nested -> nested.ne(Item::getPublisherCampusKey, campusKey)
                            .or().isNull(Item::getPublisherCampusKey));
                } else if (dormitoryKey != null) {
                    wrapper.and(nested -> nested.ne(Item::getPublisherDormitoryKey, dormitoryKey)
                            .or().isNull(Item::getPublisherDormitoryKey));
                }
            }
            case ALL -> { }
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

    // ================================================================
    // 辅助
    // ================================================================

    private String normalizedType(String type) {
        if (!StringUtils.hasText(type)) {
            return null;
        }
        try {
            return ItemType.from(type).code();
        } catch (IllegalArgumentException invalidType) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品类型不合法");
        }
    }

    private List<String> normalizedTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        return tags.stream()
                .filter(StringUtils::hasText)
                .map(t -> t.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
    }

    private String normalizeSort(String sort) {
        if (!StringUtils.hasText(sort) || "random".equals(sort.trim())) {
            return "random";
        }
        return switch (sort.trim()) {
            case "latest", "priceAsc", "priceDesc", "views" -> sort.trim();
            default -> throw new BusinessException(ResultCode.BAD_REQUEST, "排序方式不合法");
        };
    }

    private String locationKey(String value) {
        return StringUtils.hasText(value)
                ? value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT)
                : null;
    }

    private long epochSecond(LocalDateTime time) {
        return time == null ? 0 : time.atZone(ZoneId.systemDefault()).toInstant().getEpochSecond();
    }

    private LocalDateTime fromEpochSecond(long epochSecond) {
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSecond), ZoneId.systemDefault());
    }

    private Long priceCents(Item item) {
        return item.getPrice() == null ? 0L : item.getPrice().movePointRight(2).longValue();
    }

    private FeedCursorState copy(FeedCursorState state) {
        FeedCursorState next = new FeedCursorState();
        next.userId = state.userId;
        next.filterHash = state.filterHash;
        next.profileVersion = state.profileVersion;
        next.snapshotMaxItemId = state.snapshotMaxItemId;
        next.snapshotMaxRevision = state.snapshotMaxRevision;
        next.sort = state.sort;
        next.tierIndex = state.tierIndex;
        next.lastSortKey = state.lastSortKey;
        next.lastSecondaryKey = state.lastSecondaryKey;
        next.lastItemId = state.lastItemId;
        next.estimatedTotal = state.estimatedTotal;
        next.expiresAtEpochSecond = state.expiresAtEpochSecond;
        return next;
    }

    private String filterHash(Criteria criteria, String sort) {
        String type = normalizedType(criteria.type()) == null ? "" : normalizedType(criteria.type());
        Map<String, String> canonical = new TreeMap<>();
        canonical.put("sort", sort);
        canonical.put("keyword", StringUtils.hasText(criteria.keyword()) ? criteria.keyword().trim() : "");
        canonical.put("categoryId", criteria.categoryId() == null ? "" : criteria.categoryId().toString());
        canonical.put("minPrice", criteria.minPrice() == null ? "" : criteria.minPrice().toPlainString());
        canonical.put("maxPrice", criteria.maxPrice() == null ? "" : criteria.maxPrice().toPlainString());
        canonical.put("type", type);
        canonical.put("tags", String.join(",", normalizedTags(criteria.tags())));
        return sha256Hex(canonical.toString());
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (Exception unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    public record Criteria(String keyword,
                           Long categoryId,
                           BigDecimal minPrice,
                           BigDecimal maxPrice,
                           String sort,
                           String type,
                           List<String> tags) {
    }

    private enum FeedTier {
        SAME_BUILDING, SAME_CAMPUS, OTHERS, ALL
    }
}
