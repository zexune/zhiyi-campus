package com.zhiyi.module.trade.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 模拟充值请求体
 */
@Data
public class RechargeDTO {

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额不能小于0.01元")
    @DecimalMax(value = "10000.00", message = "单次充值不能超过10000元")
    private BigDecimal amount;
}
