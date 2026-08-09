package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.module.item.entity.ItemTag;
import com.zhiyi.module.item.entity.Tag;
import com.zhiyi.module.item.mapper.ItemTagMapper;
import com.zhiyi.module.item.mapper.TagMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ItemTagService {

    private final TagMapper tagMapper;
    private final ItemTagMapper itemTagMapper;
    private final TagQueryService tagQueryService;

    @Transactional
    public void replaceTags(Long itemId, Long schoolId, List<String> names) {
        itemTagMapper.delete(new LambdaUpdateWrapper<ItemTag>().eq(ItemTag::getItemId, itemId));
        for (TagName tagName : normalize(names)) {
            Tag tag = findOrCreate(tagName);
            ItemTag relation = new ItemTag();
            relation.setItemId(itemId);
            relation.setTagId(tag.getId());
            itemTagMapper.insert(relation);
        }
        tagQueryService.invalidate(schoolId);
    }

    @Transactional
    public void deleteTags(Long itemId, Long schoolId) {
        itemTagMapper.delete(new LambdaUpdateWrapper<ItemTag>().eq(ItemTag::getItemId, itemId));
        tagQueryService.invalidate(schoolId);
    }

    public Map<Long, List<String>> tagsByItemIds(Set<Long> itemIds) {
        if (itemIds == null || itemIds.isEmpty()) return Map.of();
        List<ItemTag> relations = itemTagMapper.selectList(new LambdaQueryWrapper<ItemTag>()
                .in(ItemTag::getItemId, itemIds)
                .orderByAsc(ItemTag::getItemId, ItemTag::getTagId));
        Set<Long> tagIds = relations.stream().map(ItemTag::getTagId).collect(Collectors.toSet());
        Map<Long, Tag> tags = tagIds.isEmpty()
                ? Map.of()
                : tagMapper.selectByIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
        Map<Long, List<String>> result = new LinkedHashMap<>();
        for (ItemTag relation : relations) {
            Tag tag = tags.get(relation.getTagId());
            if (tag != null) {
                result.computeIfAbsent(relation.getItemId(), ignored -> new ArrayList<>()).add(tag.getName());
            }
        }
        result.replaceAll((ignored, value) -> List.copyOf(value));
        return result;
    }

    private Tag findOrCreate(TagName tagName) {
        Tag existing = tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                .eq(Tag::getNormalizedName, tagName.normalized())
                .last("LIMIT 1"));
        if (existing != null) return existing;

        Tag created = new Tag();
        created.setName(tagName.display());
        created.setNormalizedName(tagName.normalized());
        try {
            tagMapper.insert(created);
            return created;
        } catch (DuplicateKeyException concurrentInsert) {
            return tagMapper.selectOne(new LambdaQueryWrapper<Tag>()
                    .eq(Tag::getNormalizedName, tagName.normalized())
                    .last("LIMIT 1"));
        }
    }

    private List<TagName> normalize(List<String> names) {
        if (names == null || names.isEmpty()) return List.of();
        Map<String, TagName> unique = new LinkedHashMap<>();
        for (String raw : names) {
            if (!StringUtils.hasText(raw)) continue;
            String display = Normalizer.normalize(raw.trim(), Normalizer.Form.NFKC);
            if (display.length() > 50) display = display.substring(0, 50);
            String normalized = display.toLowerCase(Locale.ROOT);
            unique.putIfAbsent(normalized, new TagName(display, normalized));
        }
        return List.copyOf(new LinkedHashSet<>(unique.values()));
    }

    private record TagName(String display, String normalized) {
    }
}
