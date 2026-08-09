package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum PenaltyStatus implements CodeEnum {
    ACTIVE("ACTIVE"),
    REVOKED("REVOKED");

    @EnumValue
    private final String code;
}
