package com.zhiyi.module.trade.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 钱包余额返回体
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WalletBalanceVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal balance;
}
