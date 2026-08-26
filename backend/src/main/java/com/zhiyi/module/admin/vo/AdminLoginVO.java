package com.zhiyi.module.admin.vo;

import com.zhiyi.module.user.entity.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;

/** 管理员独立登录响应，只暴露后台身份所需的最小信息。 */
public record AdminLoginVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminUserVO user) {

    public static AdminLoginVO of(String token, SysUser admin) {
        return new AdminLoginVO(token, new AdminUserVO(
                admin.getId(), admin.getStudentId(), admin.getNickname(), admin.getRole().code()));
    }

    public record AdminUserVO(
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String username,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String nickname,
            @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String role) {
    }
}
