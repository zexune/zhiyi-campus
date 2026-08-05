package com.zhiyi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 注销账号（个人中心「账号安全」）—— 需输入密码二次确认
 */
@Data
public class CancelAccountDTO {

    @NotBlank(message = "请输入密码确认注销")
    private String password;
}
