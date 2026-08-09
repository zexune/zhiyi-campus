package com.zhiyi.module.item.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhiyi.module.item.mapper.ItemTagMapper;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.module.item.vo.TagAggregateRow;
import com.zhiyi.module.item.vo.TagCountVO;
import com.zhiyi.module.item.vo.TagGroupVO;
import com.zhiyi.module.item.vo.TagTrendVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 标签只在商品内容或可见性变化时失效；读请求直接命中校级短缓存。
 */
@Service
public class TagQueryService {

    private final ItemTagMapper itemTagMapper;
    private final Cache<Long, List<TagGroupVO>> schoolTagCache;

    public TagQueryService(ItemTagMapper itemTagMapper,
                           @Value("${zhiyi.marketplace.tag-cache-ttl:60s}") Duration ttl) {
        this.itemTagMapper = itemTagMapper;
        this.schoolTagCache = Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(ttl)
                .build();
    }

    public List<TagGroupVO> allTags(Long schoolId) {
        return schoolTagCache.get(schoolId, this::loadGroups);
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

    public void invalidate(Long schoolId) {
        if (schoolId == null) return;
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            schoolTagCache.invalidate(schoolId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                schoolTagCache.invalidate(schoolId);
            }
        });
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
