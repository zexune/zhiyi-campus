package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserStatus implements CodeEnum {
    ACTIVE("ACTIVE"),
    BANNED_TEMP("BANNED_TEMP"),
    BANNED_PERM("BANNED_PERM"),
    CANCELLED("CANCELLED");

    @EnumValue
    private final String code;
}
