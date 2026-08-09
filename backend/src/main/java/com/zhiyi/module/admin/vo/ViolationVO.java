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
    private String sellerName;
    private Long reporterId;
    private String reporterName;
    private String originalTitle;
    private String originalDescription;
    private String source;
    private String violationType;
    private String violationReason;
    private String matchedRules;
    private String ruleVersion;
    private String status;              // PENDING / CONFIRMED / DISMISSED / OVERTURNED
    private Long handlerId;
    private String handlerName;         // 处理管理员昵称
    private String handleNote;
    private Long itemId;                // 关联商品 ID（管理员可直接下架）
    private String itemStatus;          // 商品当前状态
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
