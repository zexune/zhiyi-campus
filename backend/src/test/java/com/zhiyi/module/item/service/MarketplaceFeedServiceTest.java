package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.SysUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketplaceFeedServiceTest {

    @Mock private ItemMapper itemMapper;

    @BeforeAll
    static void initializeMetadata() {
        initialize(Item.class, ItemMapper.class);
    }

    @Test
    void smartFeedUsesIndexedStableKeyWithoutRandOrUserEnumeration() {
        Page<Item> empty = new Page<>(1, 12, 0);
        empty.setRecords(List.of());
        when(itemMapper.selectPage(any(), any())).thenReturn(empty);
        SysUser viewer = new SysUser();
        viewer.setSchoolId(2L);

        new MarketplaceFeedService(itemMapper).list(
                new MarketplaceFeedService.Criteria(null, null, null, null,
                        "random", null, null), viewer, 1, 12);

        verify(itemMapper).selectPage(any(), argThat(wrapper -> {
            String sql = wrapper.getSqlSegment().toUpperCase();
            assertTrue(sql.contains("FEED_KEY"));
            assertFalse(sql.contains("RAND("));
            return true;
        }));
    }

    @Test
    void exactTagFilterUsesNormalizedRelationIndex() {
        Page<Item> empty = new Page<>(1, 12, 0);
        empty.setRecords(List.of());
        when(itemMapper.selectPage(any(), any())).thenReturn(empty);
        SysUser viewer = new SysUser();
        viewer.setSchoolId(2L);

        new MarketplaceFeedService(itemMapper).list(
                new MarketplaceFeedService.Criteria(null, null, null, null,
                        "latest", null, List.of("iPad")), viewer, 1, 12);

        verify(itemMapper).selectPage(any(), argThat(wrapper -> {
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("item_tag"));
            assertTrue(sql.contains("normalized_name"));
            assertFalse(sql.contains("tags LIKE"));
            return true;
        }));
    }
}
