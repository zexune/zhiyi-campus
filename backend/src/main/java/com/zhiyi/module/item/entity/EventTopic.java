package com.zhiyi.module.item.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.mybatis.StringListJsonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "event_topic", autoResultMap = true)
public class EventTopic {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String filterType;
    private Long filterCategoryId;
    /** 商品标签筛选：JSON 数组（零到多个），任一命中即属于专题 */
    @TableField(typeHandler = StringListJsonTypeHandler.class)
    private List<String> filterTags;
    private String bannerText;
    private Boolean enabled;
    private Long createdBy;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
