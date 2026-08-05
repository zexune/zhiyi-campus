package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.SchoolScopeGuard;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.vo.AiTagTrendVO;
import com.zhiyi.module.item.vo.FavoriteToggleVO;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.social.entity.ItemFavorite;
import com.zhiyi.module.social.mapper.ItemFavoriteMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 模块三：商品大厅、搜索筛选、收藏与排行榜。
 */
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final ItemFavoriteMapper favoriteMapper;
    private final SysUserMapper userMapper;
    private final ObjectMapper objectMapper;

    public List<Category> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
    }

    /**
     * 按商品大类聚合 AI 标签，用于前端"精细筛选"分组标签云。
     * 返回结构：[{categoryId, categoryName, tags: [{name, count}]}]
     */
    public List<Map<String, Object>> getAllTags(Long currentUserId) {
        Long schoolId = requireUserSchoolId(currentUserId);

        // 查出所有在售商品（只需 category_id + ai_tags）
        LambdaQueryWrapper<Item> itemWrapper = new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, "ON_SALE")
                .eq(Item::getSchoolId, schoolId)
                .select(Item::getCategoryId, Item::getAiTags);
        List<Item> items = itemMapper.selectList(itemWrapper);

        // categoryId → tag → count
        Map<Long, Map<String, Long>> grouped = new LinkedHashMap<>();
        for (Item item : items) {
            Long cid = item.getCategoryId();
            Map<String, Long> tagMap = grouped.computeIfAbsent(cid, k -> new LinkedHashMap<>());
            for (String tag : parseJsonArray(item.getAiTags())) {
                if (StringUtils.hasText(tag)) {
                    tagMap.merge(tag.trim(), 1L, Long::sum);
                }
            }
        }

        // 组装返回：按大类 sort_order 排序，每类内的标签按频次降序
        List<Category> sortedCats = categoryMapper.selectList(
                new LambdaQueryWrapper<Category>().orderByAsc(Category::getSortOrder));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Category cat : sortedCats) {
            Map<String, Long> tagMap = grouped.get(cat.getId());
            if (tagMap == null || tagMap.isEmpty()) continue;
            List<Map<String, Object>> tags = tagMap.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .map(e -> {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("name", e.getKey());
                        t.put("count", e.getValue());
                        return t;
                    })
                    .collect(Collectors.toList());
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("categoryId", cat.getId());
            group.put("categoryName", cat.getName());
            group.put("tags", tags);
            result.add(group);
        }
        return result;
    }

    public IPage<ItemCardVO> listOnSaleItems(String keyword,
                                            Long categoryId,
                                            BigDecimal minPrice,
                                            BigDecimal maxPrice,
                                            String sort,
                                            String type,
                                            String tag,
                                            int page,
                                            int size,
                                            Long currentUserId) {
        SysUser viewer = requireMarketplaceUser(currentUserId);
        Long schoolId = SchoolScopeGuard.requireAssigned(viewer.getSchoolId());
        NeighborPriority neighborPriority = buildNeighborPriority(viewer, sort);
        Page<Item> itemPage = itemMapper.selectPage(
                new Page<>(Math.max(page, 1), normalizeSize(size)),
                buildOnSaleWrapper(
                        keyword, categoryId, minPrice, maxPrice, sort, type, tag,
                        schoolId, neighborPriority)
        );

        Page<ItemCardVO> result = new Page<>(itemPage.getCurrent(), itemPage.getSize(), itemPage.getTotal());
        result.setRecords(toItemCards(itemPage.getRecords(), currentUserId));
        return result;
    }

    public ItemCardVO getDetail(Long itemId, Long currentUserId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        requireSameSchool(currentUserId, item, "只能查看本校商品");
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .setSql("view_count = view_count + 1")
                .set(Item::getUpdatedAt, LocalDateTime.now()));
        item.setViewCount((item.getViewCount() == null ? 0 : item.getViewCount()) + 1);
        return toItemCards(List.of(item), currentUserId).get(0);
    }

    public ItemCardVO getSnapshot(Long itemId, Long currentUserId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        return toItemCards(List.of(item), currentUserId).get(0);
    }

    public ItemCardVO getOwnItem(Long userId, Long itemId) {
        Item item = requireOwnItem(userId, itemId);
        return toItemCards(List.of(item), userId).get(0);
    }

    public List<ItemCardVO> listErrands(Long currentUserId) {
        Long schoolId = requireUserSchoolId(currentUserId);
        List<Item> items = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(Item::getSchoolId, schoolId)
                .eq(Item::getType, "ERRAND")
                .eq(Item::getStatus, "ON_SALE")
                .gt(Item::getDeadlineTime, LocalDateTime.now())
                .orderByAsc(Item::getDeadlineTime));
        return toItemCards(items, currentUserId);
    }

    public List<ItemCardVO> listSwapMatches(Long currentUserId) {
        Long schoolId = requireUserSchoolId(currentUserId);
        List<Item> mine = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(Item::getPublisherId, currentUserId)
                .eq(Item::getSchoolId, schoolId)
                .eq(Item::getType, "SWAP")
                .eq(Item::getStatus, "ON_SALE"));
        if (mine.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = mine.stream().map(Item::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Item> matches = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                .eq(Item::getSchoolId, schoolId)
                .eq(Item::getType, "SWAP")
                .eq(Item::getStatus, "ON_SALE")
                .ne(Item::getPublisherId, currentUserId)
                .in(!categoryIds.isEmpty(), Item::getCategoryId, categoryIds)
                .orderByDesc(Item::getCreatedAt));
        return toItemCards(matches, currentUserId);
    }

    public void requireVisibleItem(Long userId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        requireSameSchool(userId, item, "只能查看本校商品");
    }

    @Transactional
    public FavoriteToggleVO toggleFavorite(Long userId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        requireSameSchool(userId, item, "只能收藏本校商品");
        if (!"ON_SALE".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.ITEM_NOT_ON_SALE);
        }
        if (Objects.equals(item.getPublisherId(), userId)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不能收藏自己发布的商品");
        }

        ItemFavorite existing = favoriteMapper.selectOne(new LambdaQueryWrapper<ItemFavorite>()
                .eq(ItemFavorite::getUserId, userId)
                .eq(ItemFavorite::getItemId, itemId)
                .last("LIMIT 1"));
        boolean favorite;
        if (existing != null) {
            favoriteMapper.deleteById(existing.getId());
            favorite = false;
        } else {
            ItemFavorite record = new ItemFavorite();
            record.setUserId(userId);
            record.setItemId(itemId);
            try {
                favoriteMapper.insert(record);
            } catch (DuplicateKeyException ignored) {
                // 并发重复点击时以“已收藏”状态返回。
            }
            favorite = true;
        }
        return new FavoriteToggleVO(itemId, favorite, favoriteCount(itemId));
    }

    public IPage<ItemCardVO> listMyFavorites(Long userId, int page, int size) {
        Long schoolId = requireUserSchoolId(userId);
        Page<ItemFavorite> favPage = favoriteMapper.selectPage(
                new Page<>(Math.max(page, 1), normalizeSize(size)),
                new LambdaQueryWrapper<ItemFavorite>()
                        .eq(ItemFavorite::getUserId, userId)
                        .orderByDesc(ItemFavorite::getCreatedAt));
        List<Long> itemIds = favPage.getRecords().stream()
                .map(ItemFavorite::getItemId)
                .toList();

        List<Item> items = itemIds.isEmpty() ? List.of() : itemMapper.selectBatchIds(itemIds);
        Map<Long, Item> itemById = items.stream().collect(Collectors.toMap(Item::getId, Function.identity()));
        List<Item> ordered = itemIds.stream()
                .map(itemById::get)
                .filter(Objects::nonNull)
                // 收藏列表也以账号当前所属学校为边界。
                .filter(item -> Objects.equals(item.getSchoolId(), schoolId))
                .toList();

        Page<ItemCardVO> result = new Page<>(favPage.getCurrent(), favPage.getSize(), favPage.getTotal());
        result.setRecords(toItemCards(ordered, userId));
        return result;
    }

    public IPage<ItemCardVO> listMyItems(Long userId, String status, int page, int size) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<Item>()
                .eq(Item::getPublisherId, userId)
                .orderByDesc(Item::getCreatedAt);
        if (StringUtils.hasText(status)) {
            wrapper.eq(Item::getStatus, status.trim());
        }

        Page<Item> itemPage = itemMapper.selectPage(
                new Page<>(Math.max(page, 1), normalizeSize(size)),
                wrapper);
        Page<ItemCardVO> result = new Page<>(itemPage.getCurrent(), itemPage.getSize(), itemPage.getTotal());
        result.setRecords(toItemCards(itemPage.getRecords(), userId));
        return result;
    }

    @Transactional
    public void offShelf(Long userId, Long itemId) {
        Item item = requireOwnItem(userId, itemId);
        if (!"ON_SALE".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有在售商品可以下架");
        }
        updateStatus(itemId, "OFF_SHELF");
    }

    @Transactional
    public void deleteOwnItem(Long userId, Long itemId) {
        Item item = requireOwnItem(userId, itemId);
        if ("PENDING".equals(item.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "交易中的商品不能删除");
        }
        itemMapper.deleteById(itemId);
    }

    public List<ItemCardVO> ranking(int limit, Long currentUserId) {
        int safeLimit = Math.max(1, Math.min(limit, 20));
        Long schoolId = requireUserSchoolId(currentUserId);
        QueryWrapper<ItemFavorite> rankingWrapper = new QueryWrapper<ItemFavorite>()
                .select("item_id", "COUNT(*) AS favorite_count")
                .groupBy("item_id")
                .orderByDesc("favorite_count");
        List<Long> visibleItemIds = itemMapper.selectList(new LambdaQueryWrapper<Item>()
                        .eq(Item::getStatus, "ON_SALE")
                        .eq(Item::getSchoolId, schoolId)
                        .select(Item::getId))
                .stream()
                .map(Item::getId)
                .toList();
        if (visibleItemIds.isEmpty()) {
            return List.of();
        }
        rankingWrapper.in("item_id", visibleItemIds);
        rankingWrapper.last("LIMIT " + safeLimit * 3);
        List<Map<String, Object>> rows = favoriteMapper.selectMaps(rankingWrapper);

        Map<Long, Long> counts = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            counts.put(toLong(row.get("item_id")), toLong(row.get("favorite_count")));
        }

        List<Long> rankedIds = new ArrayList<>(counts.keySet());
        Map<Long, Item> itemMap = rankedIds.isEmpty()
                ? Map.of()
                : itemMapper.selectBatchIds(rankedIds).stream()
                .filter(item -> "ON_SALE".equals(item.getStatus()))
                .filter(item -> Objects.equals(item.getSchoolId(), schoolId))
                .collect(Collectors.toMap(Item::getId, Function.identity()));

        List<Item> items = rankedIds.stream()
                .map(itemMap::get)
                .filter(Objects::nonNull)
                .limit(safeLimit)
                .collect(Collectors.toCollection(ArrayList::new));

        if (items.size() < safeLimit) {
            LambdaQueryWrapper<Item> fillerWrapper = new LambdaQueryWrapper<Item>()
                    .eq(Item::getStatus, "ON_SALE")
                    .eq(Item::getSchoolId, schoolId)
                    .orderByDesc(Item::getCreatedAt)
                    .last("LIMIT " + (safeLimit - items.size()));
            if (!rankedIds.isEmpty()) {
                fillerWrapper.notIn(Item::getId, rankedIds);
            }
            items.addAll(itemMapper.selectList(fillerWrapper));
        }

        List<ItemCardVO> cards = toItemCards(items, currentUserId);
        for (ItemCardVO card : cards) {
            card.setFavoriteCount(counts.getOrDefault(card.getId(), 0L));
        }
        return cards;
    }

    public List<AiTagTrendVO> trendingAiTags(int limit, Long currentUserId) {
        int safeLimit = Math.max(1, Math.min(limit, 10));
        Long schoolId = requireUserSchoolId(currentUserId);
        QueryWrapper<Item> tagWrapper = new QueryWrapper<Item>()
                .select("ai_tags")
                .eq("status", "ON_SALE")
                .eq("school_id", schoolId)
                .isNotNull("ai_tags");
        List<Object> rawTagValues = itemMapper.selectObjs(tagWrapper);

        Map<String, Long> frequencies = new HashMap<>();
        for (Object rawTagValue : rawTagValues) {
            Set<String> itemTags = parseJsonArray(String.valueOf(rawTagValue)).stream()
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .filter(tag -> tag.length() <= 20)
                    .collect(Collectors.toSet());
            itemTags.forEach(tag -> frequencies.merge(tag, 1L, Long::sum));
        }

        return frequencies.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .limit(safeLimit)
                .map(entry -> new AiTagTrendVO(entry.getKey(), entry.getValue()))
                .toList();
    }

    private LambdaQueryWrapper<Item> buildOnSaleWrapper(String keyword,
                                                       Long categoryId,
                                                       BigDecimal minPrice,
                                                       BigDecimal maxPrice,
                                                       String sort,
                                                       String type,
                                                       String tag,
                                                       Long schoolId,
                                                       NeighborPriority neighborPriority) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, "ON_SALE")
                .eq(Item::getSchoolId, schoolId)
                // 过滤已过截止时间的商品（无截止时间或截止时间在未来才展示）
                .and(w -> w.isNull(Item::getDeadlineTime)
                        .or().gt(Item::getDeadlineTime, LocalDateTime.now()));
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Item::getTitle, kw)
                    .or().like(Item::getAiTags, kw)
                    .or().like(Item::getDescription, kw));
        }
        if (categoryId != null) {
            wrapper.eq(Item::getCategoryId, categoryId);
        }
        if (minPrice != null) {
            wrapper.ge(Item::getPrice, minPrice);
        }
        if (maxPrice != null) {
            wrapper.le(Item::getPrice, maxPrice);
        }
        if (StringUtils.hasText(type)) {
            wrapper.eq(Item::getType, type.trim().toUpperCase());
        }
        if (StringUtils.hasText(tag)) {
            // 用 LIKE "\"%tag%\"" 精确匹配 JSON 数组中的标签名
            // 引号包裹防止 "全新" 误匹配 "全新未拆封" 等包含关系
            String trimmed = tag.trim();
            wrapper.like(Item::getAiTags, "\"" + trimmed + "\"");
        }
        applySort(wrapper, sort, neighborPriority);
        return wrapper;
    }

    private SysUser requireMarketplaceUser(Long currentUserId) {
        if (currentUserId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        SysUser user = userMapper.selectById(currentUserId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        SchoolScopeGuard.requireAssigned(user.getSchoolId());
        return user;
    }

    private void requireSameSchool(Long userId, Item item, String message) {
        SysUser user = requireMarketplaceUser(userId);
        SchoolScopeGuard.requireSame(user.getSchoolId(), item.getSchoolId(), message);
    }

    private Long requireUserSchoolId(Long userId) {
        SysUser user = requireMarketplaceUser(userId);
        return SchoolScopeGuard.requireAssigned(user.getSchoolId());
    }

    private void applySort(LambdaQueryWrapper<Item> wrapper,
                           String sort,
                           NeighborPriority neighborPriority) {
        String normalized = StringUtils.hasText(sort) ? sort.trim() : "random";
        switch (normalized) {
            case "priceAsc" -> wrapper.orderByAsc(Item::getPrice).orderByDesc(Item::getCreatedAt);
            case "priceDesc" -> wrapper.orderByDesc(Item::getPrice).orderByDesc(Item::getCreatedAt);
            case "latest" -> wrapper.orderByDesc(Item::getCreatedAt);
            case "views" -> wrapper.orderByDesc(Item::getViewCount).orderByDesc(Item::getCreatedAt);
            case "random" -> applyNeighborSort(wrapper, neighborPriority);
            default -> wrapper.orderByDesc(Item::getCreatedAt);
        }
    }

    private NeighborPriority buildNeighborPriority(SysUser viewer, String sort) {
        String normalizedSort = StringUtils.hasText(sort) ? sort.trim() : "random";
        if (!"random".equals(normalizedSort)
                || (!StringUtils.hasText(viewer.getDormitory())
                && !StringUtils.hasText(viewer.getCampus()))) {
            return NeighborPriority.empty();
        }

        String viewerDormitory = normalizeDormitory(viewer.getDormitory());
        String viewerCampus = normalizeCampus(viewer.getCampus());
        List<SysUser> schoolUsers = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getSchoolId, viewer.getSchoolId())
                .and(wrapper -> wrapper
                        .isNotNull(SysUser::getDormitory)
                        .or()
                        .isNotNull(SysUser::getCampus))
                .select(SysUser::getId, SysUser::getCampus, SysUser::getDormitory));

        Set<Long> sameBuilding = new HashSet<>();
        Set<Long> sameCampus = new HashSet<>();
        for (SysUser user : schoolUsers) {
            String dormitory = normalizeDormitory(user.getDormitory());
            String campus = normalizeCampus(user.getCampus());
            boolean campusMatches = !viewerCampus.isEmpty() && viewerCampus.equals(campus);
            boolean campusConflicts = !viewerCampus.isEmpty()
                    && !campus.isEmpty()
                    && !campusMatches;
            if (!viewerDormitory.isEmpty()
                    && viewerDormitory.equals(dormitory)
                    && !campusConflicts) {
                sameBuilding.add(user.getId());
            } else if (campusMatches) {
                sameCampus.add(user.getId());
            }
        }
        return new NeighborPriority(sameBuilding, sameCampus);
    }

    private void applyNeighborSort(LambdaQueryWrapper<Item> wrapper,
                                   NeighborPriority neighborPriority) {
        if (neighborPriority == null || neighborPriority.isEmpty()) {
            wrapper.last("ORDER BY RAND()");
            return;
        }

        List<String> cases = new ArrayList<>();
        if (!neighborPriority.sameBuilding().isEmpty()) {
            cases.add("WHEN publisher_id IN (" + joinIds(neighborPriority.sameBuilding()) + ") THEN 0");
        }
        if (!neighborPriority.sameCampus().isEmpty()) {
            cases.add("WHEN publisher_id IN (" + joinIds(neighborPriority.sameCampus()) + ") THEN 1");
        }
        wrapper.last("ORDER BY CASE " + String.join(" ", cases) + " ELSE 2 END, RAND()");
    }

    private String joinIds(Set<Long> ids) {
        return ids.stream()
                .filter(Objects::nonNull)
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private List<ItemCardVO> toItemCards(List<Item> items, Long currentUserId) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        Set<Long> categoryIds = items.stream().map(Item::getCategoryId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> publisherIds = items.stream().map(Item::getPublisherId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> itemIds = items.stream().map(Item::getId).collect(Collectors.toCollection(HashSet::new));

        Map<Long, Category> categories = selectCategoryMap(categoryIds);
        Map<Long, SysUser> users = selectUserMap(publisherIds);
        Map<Long, Long> favoriteCounts = favoriteCounts(itemIds);
        Set<Long> myFavorites = currentUserId == null ? Collections.emptySet() : favoriteItemIds(currentUserId, itemIds);
        SysUser viewer = currentUserId == null ? null : userMapper.selectById(currentUserId);

        return items.stream().map(item -> {
            Category category = categories.get(item.getCategoryId());
            SysUser publisher = users.get(item.getPublisherId());
            ItemCardVO vo = new ItemCardVO();
            vo.setId(item.getId());
            vo.setPublisherId(item.getPublisherId());
            if (publisher != null) {
                vo.setPublisherNickname(publisher.getNickname());
                vo.setPublisherLevel(publisher.getLevel());
                vo.setPublisherLevelTitle(LevelRule.titleOf(publisher.getLevel()));
                vo.setPublisherVerified(StringUtils.hasText(publisher.getSchoolEmail()));
                vo.setDormitoryRelation(proximityRelation(viewer, publisher));
            }
            vo.setType(item.getType());
            vo.setTitle(item.getTitle());
            vo.setDescription(item.getDescription());
            vo.setCategoryId(item.getCategoryId());
            vo.setCategoryName(category == null ? null : category.getName());
            vo.setPrice(item.getPrice());
            List<String> images = parseJsonArray(item.getImages());
            vo.setImages(images);
            vo.setCoverImage(images.isEmpty() ? "" : images.get(0));
            vo.setAiTags(parseJsonArray(item.getAiTags()));
            vo.setTradeLocation(item.getTradeLocation());
            vo.setPickupLocation(item.getPickupLocation());
            vo.setDeliveryLocation(item.getDeliveryLocation());
            vo.setDeadlineTime(item.getDeadlineTime());
            vo.setDeadlineLabel(deadlineLabel(item.getDeadlineTime()));
            vo.setStatus(item.getStatus());
            vo.setViewCount(item.getViewCount());
            vo.setFavoriteCount(favoriteCounts.getOrDefault(item.getId(), 0L));
            vo.setFavoriteByCurrentUser(myFavorites.contains(item.getId()));
            vo.setCreatedAt(item.getCreatedAt());
            vo.setUpdatedAt(item.getUpdatedAt());
            return vo;
        }).toList();
    }

    static String deadlineLabel(LocalDateTime deadline) {
        if (deadline == null) return null;
        Duration remaining = Duration.between(LocalDateTime.now(), deadline);
        if (remaining.compareTo(Duration.ofDays(7)) > 0) return null;
        // 允许同一请求内取 now 产生的毫秒级误差，确保“正好 3 天”仍落在 3-7 天区间。
        return remaining.compareTo(Duration.ofDays(3).minusSeconds(1)) >= 0 ? "⏰" : "⚠️";
    }

    private String proximityRelation(SysUser viewer, SysUser publisher) {
        if (viewer == null || publisher == null
                || !Objects.equals(viewer.getSchoolId(), publisher.getSchoolId())
                || (!StringUtils.hasText(viewer.getDormitory())
                && !StringUtils.hasText(viewer.getCampus()))) {
            return null;
        }

        String viewerDormitory = normalizeDormitory(viewer.getDormitory());
        String publisherDormitory = normalizeDormitory(publisher.getDormitory());
        String viewerCampus = normalizeCampus(viewer.getCampus());
        String publisherCampus = normalizeCampus(publisher.getCampus());
        boolean campusMatches = !viewerCampus.isEmpty() && viewerCampus.equals(publisherCampus);
        boolean campusConflicts = !viewerCampus.isEmpty()
                && !publisherCampus.isEmpty()
                && !campusMatches;
        if (!viewerDormitory.isEmpty()
                && viewerDormitory.equals(publisherDormitory)
                && !campusConflicts) {
            return "SAME_BUILDING";
        }
        return campusMatches
                ? "SAME_CAMPUS"
                : null;
    }

    private String normalizeDormitory(String dormitory) {
        return dormitory == null ? "" : dormitory.replaceAll("\\s+", "").trim();
    }

    private String normalizeCampus(String campus) {
        return campus == null ? "" : campus.replaceAll("\\s+", "").trim();
    }

    private Map<Long, Category> selectCategoryMap(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return categoryMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private Map<Long, SysUser> selectUserMap(Set<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        return userMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
    }

    private Map<Long, Long> favoriteCounts(Set<Long> itemIds) {
        if (itemIds.isEmpty()) return Map.of();
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

    private Long favoriteCount(Long itemId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<ItemFavorite>()
                .eq(ItemFavorite::getItemId, itemId));
    }

    private Set<Long> favoriteItemIds(Long userId, Set<Long> itemIds) {
        if (itemIds.isEmpty()) return Set.of();
        return favoriteMapper.selectList(new LambdaQueryWrapper<ItemFavorite>()
                        .eq(ItemFavorite::getUserId, userId)
                        .in(ItemFavorite::getItemId, itemIds))
                .stream()
                .map(ItemFavorite::getItemId)
                .collect(Collectors.toSet());
    }

    private List<String> parseJsonArray(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            List<String> values = objectMapper.readValue(raw, new TypeReference<>() {});
            return values == null ? List.of() : values;
        } catch (Exception ignored) {
            return List.of(raw);
        }
    }

    private Item requireOwnItem(Long userId, Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        }
        if (!Objects.equals(item.getPublisherId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        return item;
    }

    private void updateStatus(Long itemId, String status) {
        Item item = new Item();
        item.setId(itemId);
        item.setStatus(status);
        itemMapper.updateById(item);
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return 0L;
        return Long.parseLong(String.valueOf(value));
    }

    private record NeighborPriority(Set<Long> sameBuilding, Set<Long> sameCampus) {
        private static NeighborPriority empty() {
            return new NeighborPriority(Set.of(), Set.of());
        }

        private boolean isEmpty() {
            return sameBuilding.isEmpty() && sameCampus.isEmpty();
        }
    }
}
