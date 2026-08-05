package com.zhiyi.module.item.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("item")
public class Item {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long publisherId;
    /** 发布时固化发布者所属学校，用于大厅、搜索和排行的数据隔离。 */
    private Long schoolId;
    private String type;            // SELL / BUY / SWAP / ERRAND
    private String title;
    private String description;
    private Long categoryId;
    private BigDecimal price;
    private String images;          // JSON 数组
    private String aiTags;          // JSON 数组
    private Boolean aiReviewed;
    private String tradeLocation;
    private String pickupLocation;
    private String deliveryLocation;
    private LocalDateTime deadlineTime;
    private String status;          // ON_SALE / PENDING / SOLD / OFF_SHELF
    private Integer viewCount;
    @TableLogic
    private Boolean isDeleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
