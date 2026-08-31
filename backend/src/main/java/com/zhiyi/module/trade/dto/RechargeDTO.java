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

    /** 单笔充值上限的唯一真相源：Bean Validation 注解与 WalletService 防御分支共用。 */
    public static final String MAX_AMOUNT_TEXT = "10000.00";

    @NotNull(message = "充值金额不能为空")
    @DecimalMin(value = "0.01", message = "充值金额不能小于0.01元")
    @DecimalMax(value = MAX_AMOUNT_TEXT, message = "单次充值不能超过10000元")
    private BigDecimal amount;
}
