package com.zhiyi.module.item.service;

import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.mapper.ItemTagMapper;
import com.zhiyi.module.item.vo.TagAggregateRow;
import com.zhiyi.module.item.vo.TagCountVO;
import com.zhiyi.module.item.vo.TagGroupVO;
import com.zhiyi.module.item.vo.TagTrendVO;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签聚合查询 —— 主库直读（B7 根因修复）。
 *
 * 本地 Caffeine 缓存已删除：不存在"写事务提交并失效后，旧读请求又把旧值放回缓存"
 * 的窗口，也不存在多实例各持一份标签缓存的问题。查询命中
 * item (school_id, status, moderation_status, is_deleted) 前缀索引 +
 * item_tag / tag 等值连接；若实测超过 SLO，再另行设计"事务内推进版本号 +
 * 版本化分布式缓存键"，不得恢复无版本的提交后删除模式。
 */
@Service
public class TagQueryService {

    private final ItemTagMapper itemTagMapper;

    public TagQueryService(ItemTagMapper itemTagMapper) {
        this.itemTagMapper = itemTagMapper;
    }

    public List<TagGroupVO> allTags(Long schoolId) {
        return loadGroups(schoolId);
    }

    /** 标签趋势窗口：只统计近 N 天新发布商品的标签，窗口外的存量不计入。 */
    private static final int TREND_WINDOW_DAYS = 7;
    /** 趋势榜条数上限（与前端"TAG TOP 10"展示一致）。 */
    private static final int MAX_TREND_LIMIT = 10;

    public List<TagTrendVO> trending(Long schoolId, int limit) {
        // 时间窗口聚合由 SQL 完成（DB 时间基准），不再基于存量 allTags 结果做内存排序
        return itemTagMapper.selectRecentTagTrends(schoolId, ItemStatus.ON_SALE,
                ModerationStatus.PASSED, TREND_WINDOW_DAYS, Math.max(1, Math.min(limit, MAX_TREND_LIMIT)));
    }

    private List<TagGroupVO> loadGroups(Long schoolId) {
        List<TagAggregateRow> rows = itemTagMapper.selectVisibleTagAggregates(
                schoolId, ItemStatus.ON_SALE, ModerationStatus.PASSED);
        Map<Long, MutableGroup> groups = new LinkedHashMap<>();
        for (TagAggregateRow row : rows) {
            MutableGroup group = groups.computeIfAbsent(row.categoryId(),
                    ignored -> new MutableGroup(row.categoryId(), row.categoryName()));
            group.tags.add(new TagCountVO(row.tagName(), row.itemCount()));
        }
        return groups.values().stream()
                .map(group -> new TagGroupVO(group.categoryId, group.categoryName, List.copyOf(group.tags)))
                .toList();
    }

    private static final class MutableGroup {
        private final Long categoryId;
        private final String categoryName;
        private final List<TagCountVO> tags = new ArrayList<>();

        private MutableGroup(Long categoryId, String categoryName) {
            this.categoryId = categoryId;
            this.categoryName = categoryName;
        }
    }
}
