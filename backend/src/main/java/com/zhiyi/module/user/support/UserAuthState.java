package com.zhiyi.module.user.support;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户鉴权状态快照 —— JwtInterceptor 每个请求都要校验的最小字段集。
 * 由 UserStateCache 缓存，避免高并发下每个请求都查 sys_user 表。
 */
@Data
@AllArgsConstructor
public class UserAuthState {
    private Long userId;
    private String role;
    private String status;                      // ACTIVE / BANNED_TEMP / BANNED_PERM
    private LocalDateTime banUntilTime;
    private Integer tokenVersion;               // 必须与 JWT 中的版本一致
}
