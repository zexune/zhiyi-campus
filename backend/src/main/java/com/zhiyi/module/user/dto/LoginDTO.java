package com.zhiyi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 登录请求（需求 1.2）
 */
@Data
public class LoginDTO {

    @NotNull(message = "请选择所属学校")
    private Long schoolId;

    @NotBlank(message = "学号不能为空")
    private String studentId;

    @NotBlank(message = "密码不能为空")
    private String password;
}
