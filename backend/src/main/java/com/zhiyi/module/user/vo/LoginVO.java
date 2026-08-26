package com.zhiyi.module.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录 / 注册成功返回：JWT + 用户信息
 */
@Data
@AllArgsConstructor
public class LoginVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String token;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private UserVO user;
}
