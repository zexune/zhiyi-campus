package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ItemStatus implements CodeEnum {
    ON_SALE("ON_SALE"),
    SOLD("SOLD"),
    OFF_SHELF("OFF_SHELF");

    @EnumValue
    private final String code;
}
