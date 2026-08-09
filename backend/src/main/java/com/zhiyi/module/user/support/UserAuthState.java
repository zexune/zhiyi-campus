package com.zhiyi.module.user.support;

import java.time.LocalDateTime;

/**
 * 用户鉴权状态的不可变最小快照。
 */
public record UserAuthState(
        Long userId,
        String role,
        String status,
        LocalDateTime banUntilTime,
        Integer tokenVersion) {
}
