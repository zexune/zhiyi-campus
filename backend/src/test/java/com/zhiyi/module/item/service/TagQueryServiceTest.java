package com.zhiyi.module.item.service;

import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.mapper.ItemTagMapper;
import com.zhiyi.module.item.vo.TagAggregateRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TagQueryServiceTest {

    @Mock private ItemTagMapper itemTagMapper;
    private TagQueryService service;

    @BeforeEach
    void setUp() {
        service = new TagQueryService(itemTagMapper, Duration.ofMinutes(1));
    }

    @Test
    void cachesSchoolTagAggregationAndReloadsAfterInvalidation() {
        when(itemTagMapper.selectVisibleTagAggregates(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED))
                .thenReturn(List.of(
                        new TagAggregateRow(2L, "教材书籍", 2, "高数", 8L),
                        new TagAggregateRow(2L, "教材书籍", 2, "有笔记", 5L)));

        assertEquals(2, service.allTags(3L).getFirst().tags().size());
        assertEquals(2, service.allTags(3L).getFirst().tags().size());
        verify(itemTagMapper).selectVisibleTagAggregates(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED);

        service.invalidate(3L);
        service.allTags(3L);

        verify(itemTagMapper, times(2)).selectVisibleTagAggregates(
                3L, ItemStatus.ON_SALE, ModerationStatus.PASSED);
    }
}
