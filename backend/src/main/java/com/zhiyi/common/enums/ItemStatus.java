package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ItemStatus implements CodeEnum {
    ON_SALE("ON_SALE"),
    /** 交易中：恰好对应一笔 WAITING_MEET 订单（item.status 是可交易性唯一权威来源）。 */
    RESERVED("RESERVED"),
    SOLD("SOLD"),
    OFF_SHELF("OFF_SHELF");

    @EnumValue
    private final String code;
}
