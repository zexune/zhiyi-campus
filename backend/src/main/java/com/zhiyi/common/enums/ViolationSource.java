package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ViolationSource implements CodeEnum {
    LOCAL_RULE("LOCAL_RULE"),
    USER_REPORT("USER_REPORT"),
    CORRECTION("CORRECTION");

    @EnumValue
    private final String code;
}
