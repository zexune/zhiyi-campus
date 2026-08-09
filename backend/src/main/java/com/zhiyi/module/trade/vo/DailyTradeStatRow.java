package com.zhiyi.module.trade.vo;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DailyTradeStatRow(LocalDate tradeDate, Long tradeCount, BigDecimal totalAmount) {
}
