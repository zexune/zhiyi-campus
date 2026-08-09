package com.zhiyi.module.item.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.mapper.ItemFavoriteMapper;
import com.zhiyi.module.trade.mapper.ItemReservationMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    @Mock private ItemMapper itemMapper;
    @Mock private CategoryMapper categoryMapper;
    @Mock private ItemFavoriteMapper favoriteMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ItemReservationMapper reservationMapper;
    @Mock private MarketplaceFeedService feedService;
    @Mock private ItemCardAssembler itemCardAssembler;
    @Mock private ItemRankingService rankingService;
    @Mock private TagQueryService tagQueryService;
    @Mock private ItemTagService itemTagService;
    private MarketplaceService service;

    @BeforeEach
    void setUp() {
        service = new MarketplaceService(itemMapper, categoryMapper, favoriteMapper, userMapper,
                reservationMapper, feedService, itemCardAssembler, rankingService,
                tagQueryService, itemTagService);
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
    }

    @Test
    void rejectsUnknownOwnItemStatusInsteadOfSilentlyReturningWrongData() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> service.listMyItems(7L, "ON-SALE", 1, 10));

        assertEquals(400, error.getCode());
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
