package com.zhiyi.module.trade.vo;

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
    private BigDecimal balance;
}
