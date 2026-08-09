package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum BanActionType implements CodeEnum {
    BAN_TEMP("BAN_TEMP"),
    BAN_PERM("BAN_PERM");

    @EnumValue
    private final String code;
}
