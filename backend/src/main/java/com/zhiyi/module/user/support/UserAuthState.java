package com.zhiyi.module.user.support;

import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;

import java.time.LocalDateTime;

/**
 * 用户鉴权状态的不可变最小快照。
 */
public record UserAuthState(
        Long userId,
        UserRole role,
        UserStatus status,
        LocalDateTime banUntilTime,
        Integer tokenVersion) {
}
