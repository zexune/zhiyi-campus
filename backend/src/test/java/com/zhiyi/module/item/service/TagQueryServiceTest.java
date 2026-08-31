package com.zhiyi.module.item.service;

import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.mapper.ItemTagMapper;
import com.zhiyi.module.item.vo.TagAggregateRow;
import com.zhiyi.module.item.vo.TagTrendVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：TagQueryService 本地缓存与 invalidate 已删除，主库直读；
 * 构造器只剩 ItemTagMapper。
 */
@ExtendWith(MockitoExtension.class)
class TagQueryServiceTest {

    @Mock private ItemTagMapper itemTagMapper;
    private TagQueryService service;

    @BeforeEach
    void setUp() {
        service = new TagQueryService(itemTagMapper);
    }

    @Test
    void aggregatesRowsIntoOrderedGroups() {
        when(itemTagMapper.selectVisibleTagAggregates(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED))
                .thenReturn(List.of(
                        new TagAggregateRow(2L, "教材书籍", 2, "高数", 8L),
                        new TagAggregateRow(2L, "教材书籍", 2, "有笔记", 5L)));

        var groups = service.allTags(3L);

        assertEquals(1, groups.size());
        assertEquals("教材书籍", groups.getFirst().categoryName());
        assertEquals(2, groups.getFirst().tags().size());
        assertEquals(8L, groups.getFirst().tags().get(0).count());
    }

    @Test
    void readsThroughToPrimaryOnEveryCall() {
        // 主库直读：无缓存层，每次查询都触达数据库
        when(itemTagMapper.selectVisibleTagAggregates(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED))
                .thenReturn(List.of(
                        new TagAggregateRow(2L, "教材书籍", 2, "高数", 8L),
                        new TagAggregateRow(2L, "教材书籍", 2, "有笔记", 5L)));

        assertEquals(2, service.allTags(3L).getFirst().tags().size());
        assertEquals(2, service.allTags(3L).getFirst().tags().size());

        verify(itemTagMapper, times(2)).selectVisibleTagAggregates(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED);
    }

    @Test
    void trendingDelegatesToWindowedAggregateWithClampedLimit() {
        // 趋势榜改为 SQL 时间窗口聚合：不再基于存量 allTags 排序，limit 钳制到 1..10
        when(itemTagMapper.selectRecentTagTrends(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED, 7, 10))
                .thenReturn(List.of(
                        new TagTrendVO("台灯", 8L),
                        new TagTrendVO("高数", 8L),
                        new TagTrendVO("考研", 2L)));

        var trending = service.trending(3L, 99);

        assertEquals(3, trending.size());
        assertEquals("台灯", trending.get(0).tag());
        assertEquals("高数", trending.get(1).tag());

        verify(itemTagMapper).selectRecentTagTrends(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED, 7, 10);
        verify(itemTagMapper, never()).selectVisibleTagAggregates(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED);
    }
}
