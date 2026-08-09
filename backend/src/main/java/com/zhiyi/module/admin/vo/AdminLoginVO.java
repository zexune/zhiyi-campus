package com.zhiyi.module.admin.vo;

import com.zhiyi.module.user.entity.SysUser;

/** 管理员独立登录响应，只暴露后台身份所需的最小信息。 */
public record AdminLoginVO(String token, AdminUserVO user) {

    public static AdminLoginVO of(String token, SysUser admin) {
        return new AdminLoginVO(token, new AdminUserVO(
                admin.getId(), admin.getStudentId(), admin.getNickname(), admin.getRole()));
    }

    public record AdminUserVO(Long id, String username, String nickname, String role) {
    }
}
