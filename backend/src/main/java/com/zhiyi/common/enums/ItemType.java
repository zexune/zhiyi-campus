package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

@Getter
@RequiredArgsConstructor
public enum ItemType implements CodeEnum {
    SELL("SELL"),
    BUY("BUY"),
    SWAP("SWAP"),
    ERRAND("ERRAND");

    @EnumValue
    private final String code;

    public static ItemType from(String value) {
        if (value == null) return null;
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
