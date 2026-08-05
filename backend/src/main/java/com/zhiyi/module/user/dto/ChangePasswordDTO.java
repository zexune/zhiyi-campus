package com.zhiyi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改密码（个人中心「账号安全」）—— 需验证原密码，新密码不得与原密码相同
 */
@Data
public class ChangePasswordDTO {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码不少于 6 位")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
