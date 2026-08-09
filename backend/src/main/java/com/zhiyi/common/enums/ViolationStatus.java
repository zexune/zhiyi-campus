package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ViolationStatus implements CodeEnum {
    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    DISMISSED("DISMISSED"),
    OVERTURNED("OVERTURNED");

    @EnumValue
    private final String code;

    public static ViolationStatus fromNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
