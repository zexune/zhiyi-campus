package com.zhiyi.module.item.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.support.ViewCountBuffer;
import com.zhiyi.module.social.mapper.ItemFavoriteMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：ItemReservationMapper 已移除（RESERVED 状态即预占），
 * 新增 ViewCountBuffer（详情浏览量走独立统计缓冲，不再触碰 item 行）。
 */
@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    @Mock private ItemMapper itemMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ItemFavoriteMapper favoriteMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private MarketplaceFeedService feedService;
    @Mock private ItemCardAssembler itemCardAssembler;
    @Mock private ItemRankingService rankingService;
    @Mock private TagQueryService tagQueryService;
    @Mock private ItemTagService itemTagService;
    @Mock private ViewCountBuffer viewCountBuffer;
    private MarketplaceService service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceService(itemMapper, categoryMapper, favoriteMapper, userMapper,
                feedService, itemCardAssembler, rankingService,
                tagQueryService, itemTagService, viewCountBuffer);
    }

    @Test
    void rejectsCrossSchoolFavorite() {
        Item item = visibleItem(100L, 2L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(userMapper.selectById(7L)).thenReturn(user(7L, 1L));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.toggleFavorite(7L, 100L));

        assertEquals(403, error.getCode());
    }

    @Test
    void rejectsCrossSchoolItemDetailForLoggedInViewer() {
        when(itemMapper.selectById(100L)).thenReturn(visibleItem(100L, 2L));
        when(userMapper.selectById(7L)).thenReturn(user(7L, 1L));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.getDetail(100L, 7L));

        assertEquals(403, error.getCode());
        // 跨校拒绝时不应记录浏览量
        verify(viewCountBuffer, never()).record(any());
    }

    @Test
    void rejectsUnknownOwnItemStatusInsteadOfSilentlyReturningWrongData() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listMyItems(7L, "ON-SALE", 1, 10));

        assertEquals(400, error.getCode());
    }

    @Test
    void reservedItemCannotBeFavorited() {
        Item item = visibleItem(100L, 1L);
        item.setStatus(ItemStatus.RESERVED); // 交易中：状态派生预占
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(userMapper.selectById(7L)).thenReturn(user(7L, 1L));

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.toggleFavorite(7L, 100L));

        assertEquals(2001, error.getCode());
        verify(favoriteMapper, never()).insert(any(com.zhiyi.module.social.entity.ItemFavorite.class));
    }

    @Test
    void offShelfUsesConditionalUpdate() {
        Item item = visibleItem(100L, 1L);
        item.setPublisherId(7L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(itemMapper.update(any(), any())).thenReturn(1);

        service.offShelf(7L, 100L);

        // 条件状态迁移（ON_SALE → OFF_SHELF），而非整行覆盖
        verify(itemMapper).update(any(), any());
        verify(itemMapper, never()).updateById(any(Item.class));
    }

    @Test
    void offShelfConflictWhenItemJustReserved() {
        Item item = visibleItem(100L, 1L);
        item.setPublisherId(7L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(itemMapper.update(any(), any())).thenReturn(0); // 并发下单抢先迁移

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.offShelf(7L, 100L));

        assertEquals(409, error.getCode());
    }

    @Test
    void reservedItemCannotBeOffShelved() {
        Item item = visibleItem(100L, 1L);
        item.setPublisherId(7L);
        item.setStatus(ItemStatus.RESERVED);
        when(itemMapper.selectById(100L)).thenReturn(item);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.offShelf(7L, 100L));

        assertEquals(409, error.getCode());
        verify(itemMapper, never()).update(any(), any());
    }

    @Test
    void deleteOwnItemFailsWhenConcurrentlyReserved() {
        // §7.1 "下单 vs 删除" 交错的单元级决胜点：无锁读为 ON_SALE 后
        // 并发下单置 RESERVED → 条件软删不匹配（0 行）→ CONFLICT，商品未删除
        Item item = visibleItem(100L, 1L);
        item.setPublisherId(7L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(itemMapper.softDeleteEditable(100L, 7L)).thenReturn(0);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.deleteOwnItem(7L, 100L));

        assertEquals(409, error.getCode());
        verify(itemTagService, never()).deleteTags(any());
    }

    @Test
    void deleteOwnItemRejectsSoldItem() {
        Item item = visibleItem(100L, 1L);
        item.setPublisherId(7L);
        item.setStatus(ItemStatus.SOLD);
        when(itemMapper.selectById(100L)).thenReturn(item);

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.deleteOwnItem(7L, 100L));

        assertEquals(400, error.getCode());
        verify(itemMapper, never()).softDeleteEditable(any(), any());
    }

    @Test
    void deleteOwnItemUsesConditionalSoftDelete() {
        Item item = visibleItem(100L, 1L);
        item.setPublisherId(7L);
        when(itemMapper.selectById(100L)).thenReturn(item);
        when(itemMapper.softDeleteEditable(100L, 7L)).thenReturn(1);

        service.deleteOwnItem(7L, 100L);

        verify(itemMapper).softDeleteEditable(100L, 7L);
        verify(itemTagService).deleteTags(100L);
        // 不再走无条件 deleteById（check-then-act 已被条件软删取代）
        verify(itemMapper, never()).deleteById(any(Long.class));
    }

    private Item visibleItem(Long id, Long schoolId) {
        Item item = new Item();
        item.setId(id);
        item.setPublisherId(8L);
        item.setSchoolId(schoolId);
        item.setStatus(ItemStatus.ON_SALE);
        item.setModerationStatus(ModerationStatus.PASSED);
        return item;
    }

    private SysUser user(Long id, Long schoolId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setSchoolId(schoolId);
        return user;
    }
}
