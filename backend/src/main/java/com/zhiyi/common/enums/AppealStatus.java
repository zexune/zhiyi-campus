package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum AppealStatus implements CodeEnum {
    PENDING("PENDING"),
    APPROVED("APPROVED"),
    REJECTED("REJECTED");

    @EnumValue
    private final String code;

    public static AppealStatus fromNullable(String value) {
        if (value == null || value.isBlank()) return null;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
