package com.zhiyi.module.trade.vo;

import com.zhiyi.common.enums.WalletLogType;
import com.zhiyi.module.trade.entity.WalletLog;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资金流水对外响应（P8：Controller 不再直返 .entity 包类型）。
 * 字段与旧 WalletLog 实体序列化保持一致，前端 wire 兼容。
 */
@Schema(description = "资金流水")
public record WalletLogResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        Long userId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WalletLogType type,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal amount,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal balanceAfter,
        Long orderId,
        String remark,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime createdAt) {

    public static WalletLogResponse from(WalletLog log) {
        return new WalletLogResponse(log.getId(), log.getUserId(), log.getType(),
                log.getAmount(), log.getBalanceAfter(), log.getOrderId(),
                log.getRemark(), log.getCreatedAt());
    }
}
