package com.zhiyi.module.item.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ItemType;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.mybatis.StringListJsonTypeHandler;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "item", autoResultMap = true)
public class Item {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long publisherId;
    /** 发布时固化发布者所属学校，用于大厅、搜索和排行的数据隔离。 */
    private Long schoolId;
    private ItemType type;
    private String title;
    private String description;
    private Long categoryId;
    private BigDecimal price;
    @TableField(typeHandler = StringListJsonTypeHandler.class)
    private List<String> images;
    private ModerationStatus moderationStatus;
    private String tradeLocation;
    private String pickupLocation;
    private String deliveryLocation;
    private ItemStatus status;
    /** 随机生成、稳定不变的推荐序键，替代 ORDER BY RAND()。 */
    private Long feedKey;
    private Integer viewCount;
    @TableLogic
    private Boolean isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
