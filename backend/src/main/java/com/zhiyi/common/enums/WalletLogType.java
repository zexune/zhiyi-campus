package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum WalletLogType implements CodeEnum {
    RECHARGE("RECHARGE"),
    PAYMENT("PAYMENT"),
    REFUND("REFUND"),
    INCOME("INCOME");

    @EnumValue
    private final String code;
}
