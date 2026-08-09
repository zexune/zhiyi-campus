package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ModerationStatus implements CodeEnum {
    PASSED("PASSED"),
    PENDING("PENDING"),
    REJECTED("REJECTED");

    @EnumValue
    private final String code;
}
