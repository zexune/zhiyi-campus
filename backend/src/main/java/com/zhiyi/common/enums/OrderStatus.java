package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum OrderStatus implements CodeEnum {
    WAITING_MEET("WAITING_MEET"),
    COMPLETED("COMPLETED"),
    CANCELLED("CANCELLED");

    @EnumValue
    private final String code;

    public static OrderStatus fromNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
