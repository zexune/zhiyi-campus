package com.zhiyi.module.user.vo;

import com.zhiyi.module.user.entity.ExpLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 经验值变动记录对外响应（P8：Controller 不再直返 .entity 包类型）。
 * 字段与旧 ExpLog 实体序列化保持一致，前端 wire 兼容。
 */
@Schema(description = "经验值变动记录")
public record ExpLogResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        Long userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Integer delta,
        Integer expAfter,
        Integer levelAfter,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt) {

    public static ExpLogResponse from(ExpLog log) {
        return new ExpLogResponse(log.getId(), log.getUserId(), log.getDelta(),
                log.getExpAfter(), log.getLevelAfter(), log.getReason(), log.getCreatedAt());
    }
}
