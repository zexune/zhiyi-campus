package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum UserRole implements CodeEnum {
    USER("USER"),
    ADMIN("ADMIN");

    @EnumValue
    private final String code;
}
