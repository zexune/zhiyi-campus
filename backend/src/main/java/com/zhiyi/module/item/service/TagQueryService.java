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

    public List<TagTrendVO> trending(Long schoolId, int limit) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (TagGroupVO group : allTags(schoolId)) {
            for (TagCountVO tag : group.tags()) {
                totals.merge(tag.name(), tag.count(), Long::sum);
            }
        }
        return totals.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry::getKey, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, Math.min(limit, 10)))
                .map(entry -> new TagTrendVO(entry.getKey(), entry.getValue()))
                .toList();
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
