package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.SysUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceFeedServiceTest {

    @Mock private ItemMapper itemMapper;

    @BeforeAll
    static void initializeMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.zhiyi.module.item.mapper.ItemMapper");
        TableInfoHelper.initTableInfo(assistant, Item.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void smartFeedUsesIndexedStableKeyWithoutRandOrUserEnumeration() {
        Page<Item> empty = new Page<>(1, 12, 0);
        empty.setRecords(List.of());
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(empty);
        SysUser viewer = new SysUser();
        viewer.setSchoolId(2L);

        new MarketplaceFeedService(itemMapper).list(
                new MarketplaceFeedService.Criteria(null, null, null, null,
                        "random", null, null), viewer, 1, 12);

        ArgumentCaptor<Wrapper<Item>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment().toUpperCase();
        assertTrue(sql.contains("FEED_KEY"));
        assertFalse(sql.contains("RAND("));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void exactTagFilterUsesNormalizedRelationIndex() {
        Page<Item> empty = new Page<>(1, 12, 0);
        empty.setRecords(List.of());
        when(itemMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(empty);
        SysUser viewer = new SysUser();
        viewer.setSchoolId(2L);

        new MarketplaceFeedService(itemMapper).list(
                new MarketplaceFeedService.Criteria(null, null, null, null,
                        "latest", null, "iPad"), viewer, 1, 12);

        ArgumentCaptor<Wrapper<Item>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectPage(any(Page.class), captor.capture());
        String sql = captor.getValue().getSqlSegment();
        assertTrue(sql.contains("item_tag"));
        assertTrue(sql.contains("normalized_name"));
        assertFalse(sql.contains("tags LIKE"));
    }
}
