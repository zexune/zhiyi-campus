package com.zhiyi.module.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 违规记录列表项
 */
@Data
public class ViolationVO {
    private Long id;
    private Long userId;
    private String reporterName;        // 发布者昵称
    private String originalTitle;
    private String originalDescription;
    private String violationType;
    private String violationReason;
    private String aiTags;
    private String status;              // PENDING / CONFIRMED / DISMISSED
    private Long handlerId;
    private String handlerName;         // 处理管理员昵称
    private String handleNote;
    private Long itemId;                // 关联商品 ID（管理员可直接下架）
    private String itemStatus;          // 商品当前状态
    private Boolean aiReviewError;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
