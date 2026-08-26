package com.zhiyi.module.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record AppealVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        Long reportId,
        Long itemId,
        Long userId,
        String sellerName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String itemTitle,
        String violationReason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status,
        Long handlerId,
        String handlerName,
        String handleNote,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt,
        LocalDateTime handledAt
) {
}
