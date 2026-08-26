package com.zhiyi.module.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 违规记录列表项
 */
@Data
public class ViolationVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    private Long userId;
    private String sellerName;
    private Long reporterId;
    private String reporterName;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String originalTitle;
    private String originalDescription;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String source;
    private String violationType;
    private String violationReason;
    private List<String> matchedRules;
    private String ruleVersion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;              // PENDING / CONFIRMED / DISMISSED / OVERTURNED
    private Long handlerId;
    private String handlerName;         // 处理管理员昵称
    private String handleNote;
    private Long itemId;                // 关联商品 ID（管理员可直接下架）
    private String itemStatus;          // 商品当前状态
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;
}
