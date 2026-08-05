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
        topic.setFilterTag(trimToNull(dto.getFilterTag()));
        topic.setBannerText(dto.getBannerText().trim());
        topic.setEnabled(dto.getEnabled());
        if (id == null) {
            topic.setCreatedBy(adminId);
            topicMapper.insert(topic);
        } else topicMapper.updateById(topic);
        return topic;
    }

    @Transactional
    public void delete(Long id) {
        if (topicMapper.deleteById(id) == 0) throw new BusinessException(ResultCode.NOT_FOUND, "专题不存在");
    }

    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
