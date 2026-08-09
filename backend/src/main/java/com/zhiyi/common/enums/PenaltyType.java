package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PenaltyType implements CodeEnum {
    CONTENT_WARNING("CONTENT_WARNING");

    @EnumValue
    private final String code;
}
