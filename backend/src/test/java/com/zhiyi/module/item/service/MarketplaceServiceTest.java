package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import tools.jackson.databind.json.JsonMapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.social.mapper.ItemFavoriteMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceServiceTest {

    @Mock
    private ItemMapper itemMapper;
    @Mock
    private CategoryMapper categoryMapper;
    @Mock
    private ItemFavoriteMapper favoriteMapper;
    @Mock
    private SysUserMapper userMapper;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.zhiyi.module.item.mapper.ItemMapper");
        TableInfoHelper.initTableInfo(assistant, Item.class);
        TableInfoHelper.initTableInfo(assistant, SysUser.class);
    }

    @Test
    void ranksAiTagsByDistinctOnSaleItemFrequency() {
        SysUser viewer = new SysUser();
        viewer.setId(7L);
        viewer.setSchoolId(2L);
        when(userMapper.selectById(7L)).thenReturn(viewer);
        when(itemMapper.selectObjs(any())).thenReturn(List.of(
                "[\"iPad\",\"student\",\"iPad\"]",
                "[\"iPad\",\"student\"]",
                "[\"iPad\",\"tablet\"]"
        ));
        MarketplaceService service = new MarketplaceService(
                itemMapper, categoryMapper, favoriteMapper, userMapper, JsonMapper.builder().build());

        var result = service.trendingAiTags(10, 7L);

        assertEquals(List.of("iPad", "student", "tablet"),
                result.stream().map(tag -> tag.tag()).toList());
        assertEquals(List.of(3L, 2L, 1L),
                result.stream().map(tag -> tag.count()).toList());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void prioritizesSameBuildingThenSameCampusForSmartRecommendation() {
        SysUser viewer = new SysUser();
        viewer.setId(7L);
        viewer.setSchoolId(2L);
        viewer.setCampus("宝山 校区");
        viewer.setDormitory("南区 13 号楼");
        when(userMapper.selectById(7L)).thenReturn(viewer);

        SysUser sameBuilding = new SysUser();
        sameBuilding.setId(8L);
        sameBuilding.setCampus("宝山校区");
        sameBuilding.setDormitory("南区13号楼");
        SysUser sameCampus = new SysUser();
        sameCampus.setId(9L);
        sameCampus.setCampus("宝山校区");
        sameCampus.setDormitory("南区6号楼");
        SysUser otherCampus = new SysUser();
        otherCampus.setId(10L);
        otherCampus.setCampus("嘉定校区");
        otherCampus.setDormitory("南区13号楼");
        when(userMapper.selectList(any())).thenReturn(List.of(sameBuilding, sameCampus, otherCampus));

        Page<Item> emptyPage = new Page<>(1, 12, 0);
        emptyPage.setRecords(List.of());
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        MarketplaceService service = new MarketplaceService(
                itemMapper, categoryMapper, favoriteMapper, userMapper, JsonMapper.builder().build());
        service.listOnSaleItems(null, null, null, null,
                "random", null, null, 1, 12, 7L);

        ArgumentCaptor<Wrapper<Item>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("publisher_id IN (8) THEN 0"));
        assertTrue(sql.contains("publisher_id IN (9) THEN 1"));
        assertTrue(sql.contains("RAND()"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void prioritizesSameCampusWhenViewerHasNoDormitory() {
        SysUser viewer = new SysUser();
        viewer.setId(7L);
        viewer.setSchoolId(2L);
        viewer.setCampus("延长校区");
        when(userMapper.selectById(7L)).thenReturn(viewer);

        SysUser sameCampus = new SysUser();
        sameCampus.setId(9L);
        sameCampus.setCampus("延长校区");
        when(userMapper.selectList(any())).thenReturn(List.of(sameCampus));

        Page<Item> emptyPage = new Page<>(1, 12, 0);
        emptyPage.setRecords(List.of());
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        MarketplaceService service = new MarketplaceService(
                itemMapper, categoryMapper, favoriteMapper, userMapper, JsonMapper.builder().build());
        service.listOnSaleItems(null, null, null, null,
                "random", null, null, 1, 12, 7L);

        ArgumentCaptor<Wrapper<Item>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertTrue(sql.contains("publisher_id IN (9) THEN 1"));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void filtersMarketplaceListToCurrentUsersSchool() {
        SysUser viewer = new SysUser();
        viewer.setId(7L);
        viewer.setRole("USER");
        viewer.setSchoolId(2L);
        when(userMapper.selectById(7L)).thenReturn(viewer);

        Page<Item> emptyPage = new Page<>(1, 12, 0);
        emptyPage.setRecords(List.of());
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        MarketplaceService service = new MarketplaceService(
                itemMapper, categoryMapper, favoriteMapper, userMapper, JsonMapper.builder().build());
        service.listOnSaleItems(null, null, null, null,
                "latest", null, null, 1, 12, 7L);

        ArgumentCaptor<Wrapper<Item>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        Wrapper<Item> wrapper = wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("school_id"));
        AbstractWrapper<Item, ?, ?> abstractWrapper = (AbstractWrapper<Item, ?, ?>) wrapper;
        assertTrue(abstractWrapper.getParamNameValuePairs().containsValue(2L));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void filtersAdministratorMarketplaceToAdministratorsSchool() {
        SysUser admin = new SysUser();
        admin.setId(9L);
        admin.setRole("ADMIN");
        admin.setSchoolId(1L);
        when(userMapper.selectById(9L)).thenReturn(admin);

        Page<Item> emptyPage = new Page<>(1, 12, 0);
        emptyPage.setRecords(List.of());
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(emptyPage);

        MarketplaceService service = new MarketplaceService(
                itemMapper, categoryMapper, favoriteMapper, userMapper, JsonMapper.builder().build());
        service.listOnSaleItems(null, null, null, null,
                "latest", null, null, 1, 12, 9L);

        ArgumentCaptor<Wrapper<Item>> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectPage(any(Page.class), wrapperCaptor.capture());
        AbstractWrapper<Item, ?, ?> wrapper =
                (AbstractWrapper<Item, ?, ?>) wrapperCaptor.getValue();
        assertTrue(wrapper.getSqlSegment().contains("school_id"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L));
    }

    @Test
    void rejectsCrossSchoolFavorite() {
        Item item = new Item();
        item.setId(100L);
        item.setSchoolId(2L);
        item.setStatus("ON_SALE");
        item.setPublisherId(8L);
        when(itemMapper.selectById(100L)).thenReturn(item);

        SysUser viewer = new SysUser();
        viewer.setId(7L);
        viewer.setSchoolId(1L);
        when(userMapper.selectById(7L)).thenReturn(viewer);

        MarketplaceService service = new MarketplaceService(
                itemMapper, categoryMapper, favoriteMapper, userMapper, JsonMapper.builder().build());

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.toggleFavorite(7L, 100L));
        assertEquals(403, error.getCode());
    }

    @Test
    void rejectsCrossSchoolItemDetailForLoggedInViewer() {
        Item item = new Item();
        item.setId(100L);
        item.setSchoolId(2L);
        when(itemMapper.selectById(100L)).thenReturn(item);

        SysUser viewer = new SysUser();
        viewer.setId(7L);
        viewer.setSchoolId(1L);
        when(userMapper.selectById(7L)).thenReturn(viewer);

        MarketplaceService service = new MarketplaceService(
                itemMapper, categoryMapper, favoriteMapper, userMapper, JsonMapper.builder().build());

        BusinessException error = assertThrows(
                BusinessException.class, () -> service.getDetail(100L, 7L));
        assertEquals(403, error.getCode());
    }
}
