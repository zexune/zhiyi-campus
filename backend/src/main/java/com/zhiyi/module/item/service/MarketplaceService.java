package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.SchoolScopeGuard;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemListStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.vo.FavoriteToggleVO;
import com.zhiyi.module.item.vo.ItemCardVO;
import com.zhiyi.module.item.vo.TagGroupVO;
import com.zhiyi.module.item.vo.TagTrendVO;
import com.zhiyi.module.social.entity.ItemFavorite;
import com.zhiyi.module.social.mapper.ItemFavoriteMapper;
import com.zhiyi.module.trade.mapper.ItemReservationMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 商品大厅门面：编排可见性、收藏和卖家操作，复杂查询与视图装配由独立服务负责。
 */
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ItemMapper itemMapper;
    private final CategoryMapper categoryMapper;
    private final ItemFavoriteMapper favoriteMapper;
    private final SysUserMapper userMapper;
    private final ItemReservationMapper reservationMapper;
    private final MarketplaceFeedService feedService;
    private final ItemCardAssembler itemCardAssembler;
    private final ItemRankingService rankingService;
    private final TagQueryService tagQueryService;
    private final ItemTagService itemTagService;

    public List<Category> listCategories() {
        return categoryMapper.selectList(new LambdaQueryWrapper<Category>()
                .orderByAsc(Category::getSortOrder)
                .orderByAsc(Category::getId));
    }

    public List<TagGroupVO> getAllTags(Long currentUserId) {
        return tagQueryService.allTags(requireUserSchoolId(currentUserId));
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
        MarketplaceFeedService.Criteria criteria = new MarketplaceFeedService.Criteria(
                keyword, categoryId, minPrice, maxPrice, sort, type, tag);
        IPage<Item> itemPage = feedService.list(criteria, viewer, page, size);
        Page<ItemCardVO> result = new Page<>(itemPage.getCurrent(), itemPage.getSize(), itemPage.getTotal());
        result.setRecords(itemCardAssembler.assemble(itemPage.getRecords(), currentUserId));
        return result;
    }

    public ItemCardVO getDetail(Long itemId, Long currentUserId) {
        Item item = requireItem(itemId);
        requireSameSchool(currentUserId, item, "只能查看本校商品");
        if (!Objects.equals(item.getPublisherId(), currentUserId)
                && item.getModerationStatus() != ModerationStatus.PASSED) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品正在审核或已被下架");
        }
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getId, itemId)
                .setSql("view_count = view_count + 1")
                .set(Item::getUpdatedAt, LocalDateTime.now()));
        item.setViewCount((item.getViewCount() == null ? 0 : item.getViewCount()) + 1);
        return itemCardAssembler.assemble(List.of(item), currentUserId).getFirst();
    }

    public ItemCardVO getSnapshot(Long itemId, Long currentUserId) {
        return itemCardAssembler.assemble(List.of(requireItem(itemId)), currentUserId).getFirst();
    }

    public ItemCardVO getOwnItem(Long userId, Long itemId) {
        return itemCardAssembler.assemble(List.of(requireOwnItem(userId, itemId)), userId).getFirst();
    }

    public List<ItemCardVO> listErrands(Long currentUserId) {
        Long schoolId = requireUserSchoolId(currentUserId);
        List<Item> items = itemMapper.selectList(visibleItems(schoolId)
                .eq(Item::getType, ItemType.ERRAND)
                .orderByDesc(Item::getCreatedAt)
                .orderByDesc(Item::getId));
        return itemCardAssembler.assemble(items, currentUserId);
    }

    public List<ItemCardVO> listSwapMatches(Long currentUserId) {
        Long schoolId = requireUserSchoolId(currentUserId);
        List<Item> mine = itemMapper.selectList(visibleItems(schoolId)
                .eq(Item::getPublisherId, currentUserId)
                .eq(Item::getType, ItemType.SWAP));
        if (mine.isEmpty()) return List.of();
        List<Long> categoryIds = mine.stream().map(Item::getCategoryId)
                .filter(Objects::nonNull).distinct().toList();
        List<Item> matches = itemMapper.selectList(visibleItems(schoolId)
                .eq(Item::getType, ItemType.SWAP)
                .ne(Item::getPublisherId, currentUserId)
                .in(!categoryIds.isEmpty(), Item::getCategoryId, categoryIds)
                .orderByDesc(Item::getCreatedAt)
                .orderByDesc(Item::getId));
        return itemCardAssembler.assemble(matches, currentUserId);
    }

    public void requireVisibleItem(Long userId, Long itemId) {
        Item item = requireItem(itemId);
        requireSameSchool(userId, item, "只能查看本校商品");
        if (!Objects.equals(item.getPublisherId(), userId)
                && (item.getModerationStatus() != ModerationStatus.PASSED
                || item.getStatus() != ItemStatus.ON_SALE)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "商品当前不可见");
        }
    }

    @Transactional
    public FavoriteToggleVO toggleFavorite(Long userId, Long itemId) {
        Item item = requireItem(itemId);
        requireSameSchool(userId, item, "只能收藏本校商品");
        if (item.getStatus() != ItemStatus.ON_SALE
                || item.getModerationStatus() != ModerationStatus.PASSED
                || reservationMapper.selectById(itemId) != null) {
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
                // 并发重复点击时数据库唯一键保证幂等。
            }
            favorite = true;
        }
        return new FavoriteToggleVO(itemId, favorite, favoriteCount(itemId));
    }

    public IPage<ItemCardVO> listMyFavorites(Long userId, int page, int size) {
        Long schoolId = requireUserSchoolId(userId);
        Page<ItemFavorite> favoritePage = favoriteMapper.selectPage(
                new Page<>(Math.max(page, 1), normalizeSize(size)),
                new LambdaQueryWrapper<ItemFavorite>()
                        .eq(ItemFavorite::getUserId, userId)
                        .orderByDesc(ItemFavorite::getCreatedAt));
        List<Long> itemIds = favoritePage.getRecords().stream().map(ItemFavorite::getItemId).toList();
        Map<Long, Item> items = itemIds.isEmpty() ? Map.of() : itemMapper.selectByIds(itemIds).stream()
                .filter(item -> Objects.equals(item.getSchoolId(), schoolId))
                .filter(item -> item.getModerationStatus() == ModerationStatus.PASSED)
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        List<Item> ordered = itemIds.stream().map(items::get).filter(Objects::nonNull).toList();
        Page<ItemCardVO> result = new Page<>(favoritePage.getCurrent(), favoritePage.getSize(), favoritePage.getTotal());
        result.setRecords(itemCardAssembler.assemble(ordered, userId));
        return result;
    }

    public IPage<ItemCardVO> listMyItems(Long userId, String status, int page, int size) {
        LambdaQueryWrapper<Item> wrapper = new LambdaQueryWrapper<Item>()
                .eq(Item::getPublisherId, userId)
                .orderByDesc(Item::getCreatedAt)
                .orderByDesc(Item::getId);
        if (StringUtils.hasText(status)) applyOwnItemStatus(wrapper, status);
        Page<Item> itemPage = itemMapper.selectPage(
                new Page<>(Math.max(page, 1), normalizeSize(size)), wrapper);
        Page<ItemCardVO> result = new Page<>(itemPage.getCurrent(), itemPage.getSize(), itemPage.getTotal());
        result.setRecords(itemCardAssembler.assemble(itemPage.getRecords(), userId));
        return result;
    }

    @Transactional
    public void offShelf(Long userId, Long itemId) {
        Item item = requireOwnItem(userId, itemId);
        if (item.getStatus() != ItemStatus.ON_SALE) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有在售商品可以下架");
        }
        if (item.getModerationStatus() == ModerationStatus.PENDING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核中的商品不能手动下架");
        }
        requireNotReserved(itemId);
        updateStatus(item, ItemStatus.OFF_SHELF);
    }

    @Transactional
    public void deleteOwnItem(Long userId, Long itemId) {
        Item item = requireOwnItem(userId, itemId);
        if (item.getModerationStatus() == ModerationStatus.PENDING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "审核中的商品不能删除");
        }
        requireNotReserved(itemId);
        itemTagService.deleteTags(itemId, item.getSchoolId());
        itemMapper.deleteById(itemId);
    }

    public List<ItemCardVO> ranking(int limit, Long currentUserId) {
        Long schoolId = requireUserSchoolId(currentUserId);
        return rankingService.ranking(schoolId, limit, currentUserId);
    }

    public List<TagTrendVO> trendingTags(int limit, Long currentUserId) {
        return tagQueryService.trending(requireUserSchoolId(currentUserId), limit);
    }

    private LambdaQueryWrapper<Item> visibleItems(Long schoolId) {
        return new LambdaQueryWrapper<Item>()
                .eq(Item::getSchoolId, schoolId)
                .eq(Item::getStatus, ItemStatus.ON_SALE)
                .eq(Item::getModerationStatus, ModerationStatus.PASSED)
                .eq(Item::getIsDeleted, false)
                .notExists("SELECT 1 FROM item_reservation r WHERE r.item_id = item.id");
    }

    private void applyOwnItemStatus(LambdaQueryWrapper<Item> wrapper, String value) {
        try {
            ItemListStatus status = ItemListStatus.from(value);
            switch (status) {
                case REVIEWING -> wrapper.eq(Item::getModerationStatus, ModerationStatus.PENDING);
                case ON_SALE, OFF_SHELF -> wrapper
                        .eq(Item::getStatus, status.persistedStatus())
                        .ne(Item::getModerationStatus, ModerationStatus.PENDING);
                case SOLD -> wrapper.eq(Item::getStatus, status.persistedStatus());
            }
        } catch (IllegalArgumentException invalidStatus) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "商品状态不合法");
        }
    }

    private SysUser requireMarketplaceUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
        SysUser user = userMapper.selectById(userId);
        if (user == null) throw new BusinessException(ResultCode.USER_NOT_FOUND);
        SchoolScopeGuard.requireAssigned(user.getSchoolId());
        return user;
    }

    private Long requireUserSchoolId(Long userId) {
        return SchoolScopeGuard.requireAssigned(requireMarketplaceUser(userId).getSchoolId());
    }

    private void requireSameSchool(Long userId, Item item, String message) {
        SchoolScopeGuard.requireSame(requireMarketplaceUser(userId).getSchoolId(), item.getSchoolId(), message);
    }

    private Item requireItem(Long itemId) {
        Item item = itemMapper.selectById(itemId);
        if (item == null) throw new BusinessException(ResultCode.NOT_FOUND, "商品不存在");
        return item;
    }

    private Item requireOwnItem(Long userId, Long itemId) {
        Item item = requireItem(itemId);
        if (!Objects.equals(item.getPublisherId(), userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能操作自己发布的商品");
        }
        return item;
    }

    private void requireNotReserved(Long itemId) {
        if (reservationMapper.selectById(itemId) != null) {
            throw new BusinessException(ResultCode.CONFLICT, "商品存在进行中的订单，暂不能操作");
        }
    }

    private void updateStatus(Item item, ItemStatus status) {
        Item patch = new Item();
        patch.setId(item.getId());
        patch.setStatus(status);
        itemMapper.updateById(patch);
        tagQueryService.invalidate(item.getSchoolId());
    }

    private long favoriteCount(Long itemId) {
        return favoriteMapper.selectCount(new LambdaQueryWrapper<ItemFavorite>()
                .eq(ItemFavorite::getItemId, itemId));
    }

    private int normalizeSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }
}
