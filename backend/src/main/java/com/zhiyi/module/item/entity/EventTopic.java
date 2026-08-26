package com.zhiyi.module.item.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.mybatis.StringListJsonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "event_topic", autoResultMap = true)
public class EventTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime startTime;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime endTime;
    private String filterType;
    private Long filterCategoryId;
    /** 商品标签筛选：JSON 数组（零到多个），任一命中即属于专题 */
    @TableField(typeHandler = StringListJsonTypeHandler.class)
    private List<String> filterTags;
    private String bannerText;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean enabled;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
