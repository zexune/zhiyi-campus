package com.zhiyi.module.item.service;

import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.mapper.ItemViewStatMapper;
import com.zhiyi.module.item.support.FeedCursorCodec;
import com.zhiyi.module.user.entity.SysUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：list(criteria, viewer, page, size) 已被
 * listByCursor(criteria, viewer, cursor, size)（签名游标 + keyset 分页）替代。
 */
@ExtendWith(MockitoExtension.class)
class MarketplaceFeedServiceTest {

    @Mock private ItemMapper itemMapper;
    @Mock private ItemViewStatMapper statMapper;

    private MarketplaceFeedService service;
    private FeedCursorCodec cursorCodec;

    @BeforeAll
    static void initializeMetadata() {
        initialize(Item.class, ItemMapper.class);
    }

    @BeforeEach
    void setUp() {
        cursorCodec = new FeedCursorCodec("unit-test-secret");
        service = new MarketplaceFeedService(itemMapper, statMapper, cursorCodec);
        ReflectionTestUtils.setField(service, "cursorTtlSeconds", 900L);
    }

    private void arrangeFirstPage() {
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.maxItemId()).thenReturn(0L);
        when(itemMapper.currentListingRevision()).thenReturn(0L);
        when(itemMapper.selectList(any())).thenReturn(List.of());
    }

    @Test
    void randomFeedUsesIndexedStableKeyWithoutRandOrOffset() {
        arrangeFirstPage();
        SysUser viewer = new SysUser();
        viewer.setSchoolId(2L);

        MarketplaceFeedService.FeedPage page = service.listByCursor(
                new MarketplaceFeedService.Criteria(null, null, null, null,
                        "random", null, null), viewer, null, 12);

        verify(itemMapper).selectList(argThat(wrapper -> {
            String sql = wrapper.getSqlSegment().toUpperCase();
            assertTrue(sql.contains("FEED_KEY"), () -> "sql=" + sql);
            assertFalse(sql.contains("RAND("), () -> "sql=" + sql);
            assertFalse(sql.contains("OFFSET"), () -> "sql=" + sql);
            return true;
        }));
        assertEquals(0, page.records().size());
        assertFalse(page.hasMore());
        assertNull(page.nextCursor());
    }

    @Test
    void exactTagFilterUsesNormalizedRelationIndex() {
        arrangeFirstPage();
        SysUser viewer = new SysUser();
        viewer.setSchoolId(2L);

        service.listByCursor(
                new MarketplaceFeedService.Criteria(null, null, null, null,
                        "latest", null, List.of("iPad")), viewer, null, 12);

        verify(itemMapper).selectList(argThat(wrapper -> {
            String sql = wrapper.getSqlSegment();
            assertTrue(sql.contains("item_tag"), () -> "sql=" + sql);
            assertTrue(sql.contains("normalized_name"), () -> "sql=" + sql);
            assertFalse(sql.contains("tags LIKE"), () -> "sql=" + sql);
            return true;
        }));
    }

    @Test
    void fullPageIssuesSignedCursorForNextPage() {
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.maxItemId()).thenReturn(500L);
        when(itemMapper.currentListingRevision()).thenReturn(9L);
        Item first = new Item();
        first.setId(10L);
        first.setFeedKey(7L);
        Item second = new Item();
        second.setId(9L);
        second.setFeedKey(8L);
        when(itemMapper.selectList(any())).thenReturn(new java.util.ArrayList<>(List.of(first, second)));

        SysUser viewer = new SysUser();
        viewer.setSchoolId(2L);

        MarketplaceFeedService.FeedPage page = service.listByCursor(
                new MarketplaceFeedService.Criteria(null, null, null, null,
                        "random", null, null), viewer, null, 1);

        assertEquals(1, page.records().size());
        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());

        // 签名游标可解码回下一页状态
        var state = cursorCodec.decode(page.nextCursor());
        assertNotNull(state);
        assertEquals(10L, state.lastItemId);
        // 携带快照上界：页间发布的新商品不进入旧链
        assertEquals(500L, state.snapshotMaxItemId);
        assertEquals(9L, state.snapshotMaxRevision);
    }

    @Test
    void tamperedCursorIsRejected() {
        SysUser viewer = new SysUser();
        viewer.setSchoolId(2L);

        com.zhiyi.common.BusinessException error = assertThrows(
                com.zhiyi.common.BusinessException.class,
                () -> service.listByCursor(
                        new MarketplaceFeedService.Criteria(null, null, null, null,
                                "random", null, null), viewer, "forged.cursor", 12));

        assertEquals(com.zhiyi.common.ResultCode.FEED_CURSOR_INVALID.getCode(), error.getCode());
    }
}
