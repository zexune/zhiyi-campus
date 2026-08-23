package com.zhiyi.module.item.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.item.dto.EventTopicDTO;
import com.zhiyi.module.item.entity.EventTopic;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.EventTopicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EventTopicService {
    private final EventTopicMapper topicMapper;
    private final CategoryMapper categoryMapper;

    public List<EventTopic> listAll() {
        return topicMapper.selectList(new LambdaQueryWrapper<EventTopic>().orderByDesc(EventTopic::getStartTime));
    }

    public EventTopic activeTopic() {
        LocalDateTime now = LocalDateTime.now();
        return topicMapper.selectOne(new LambdaQueryWrapper<EventTopic>()
                .eq(EventTopic::getEnabled, true).le(EventTopic::getStartTime, now).gt(EventTopic::getEndTime, now)
                .orderByDesc(EventTopic::getStartTime).last("LIMIT 1"));
    }

    @Transactional
    public EventTopic save(Long id, Long adminId, EventTopicDTO dto) {
        if (dto.getFilterCategoryId() != null && categoryMapper.selectById(dto.getFilterCategoryId()) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "专题筛选分类不存在");
        }
        EventTopic topic = id == null ? new EventTopic() : topicMapper.selectById(id);
        if (topic == null) throw new BusinessException(ResultCode.NOT_FOUND, "专题不存在");
        topic.setTitle(dto.getTitle().trim());
        topic.setStartTime(dto.getStartTime());
        topic.setEndTime(dto.getEndTime());
        topic.setFilterType(trimToNull(dto.getFilterType()));
        topic.setFilterCategoryId(dto.getFilterCategoryId());
        topic.setFilterTags(normalizeTags(dto.getFilterTags()));
        topic.setBannerText(dto.getBannerText().trim());
        topic.setEnabled(dto.getEnabled());
        if (id == null) {
            topic.setCreatedBy(adminId);
            topicMapper.insert(topic);
        } else topicMapper.updateById(topic);
        return topic;
    }

    /** 筛选标签规范化：trim、去重（忽略大小写）、限量 6 个；全空存 null */
    private List<String> normalizeTags(List<String> raw) {
        if (raw == null || raw.isEmpty()) return null;
        LinkedHashSet<String> unique = new LinkedHashSet<>();
        for (String tag : raw) {
            if (!StringUtils.hasText(tag)) continue;
            String trimmed = tag.trim();
            if (trimmed.length() < 2 || trimmed.length() > 12) continue;
            boolean duplicated = unique.stream().anyMatch(t -> t.equalsIgnoreCase(trimmed));
            if (!duplicated) unique.add(trimmed);
            if (unique.size() >= 6) break;
        }
        return unique.isEmpty() ? null : List.copyOf(unique);
    }

    @Transactional
    public void delete(Long id) {
        if (topicMapper.deleteById(id) == 0) throw new BusinessException(ResultCode.NOT_FOUND, "专题不存在");
    }

    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
