package com.zhiyi.module.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录 / 注册成功返回：JWT + 用户信息
 */
@Data
@AllArgsConstructor
public class LoginVO {
    private String token;
    private UserVO user;
}
