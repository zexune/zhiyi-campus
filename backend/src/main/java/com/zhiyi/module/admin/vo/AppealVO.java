package com.zhiyi.module.admin.vo;

import java.time.LocalDateTime;

public record AppealVO(
        Long id,
        Long reportId,
        Long itemId,
        Long userId,
        String sellerName,
        String itemTitle,
        String violationReason,
        String reason,
        String status,
        Long handlerId,
        String handlerName,
        String handleNote,
        LocalDateTime createdAt,
        LocalDateTime handledAt
) {
}
