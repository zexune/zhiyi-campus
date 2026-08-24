package com.zhiyi.common.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 订单取消原因。所有 CANCELLED 订单必须携带显式原因，审计语义不依赖 NULL。
 */
@Getter
@RequiredArgsConstructor
public enum OrderCancelReason implements CodeEnum {
    /** 买家主动取消 */
    USER_CANCEL("USER_CANCEL"),
    /** 封禁（临时/永久）事务在同一事务内自动取消买家进行中订单 */
    AUTO_CANCEL("AUTO_CANCEL"),
    /** 审核确认违规时管理员强制撤单 */
    ADMIN_FORCE("ADMIN_FORCE");

    @EnumValue
    private final String code;
}
