package com.zhiyi.module.item.vo;

import com.zhiyi.module.item.entity.EventTopic;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 专题对外响应（P8：Controller 不再直返 .entity 包类型）。
 * 字段与旧 EventTopic 实体序列化保持一致，前端 wire 兼容。
 */
@Schema(description = "活动专题")
public record EventTopicResponse(
        Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime startTime,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime endTime,
        String filterType,
        Long filterCategoryId,
        List<String> filterTags,
        String bannerText,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Boolean enabled,
        Long createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static EventTopicResponse from(EventTopic topic) {
        return new EventTopicResponse(topic.getId(), topic.getTitle(), topic.getStartTime(),
                topic.getEndTime(), topic.getFilterType(), topic.getFilterCategoryId(),
                topic.getFilterTags(), topic.getBannerText(), topic.getEnabled(),
                topic.getCreatedBy(), topic.getCreatedAt(), topic.getUpdatedAt());
    }
}
