package com.zhiyi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密保验证 + 重置密码（需求 1.3）
 */
@Data
public class ResetPasswordDTO {

    @NotNull(message = "请选择所属学校")
    private Long schoolId;

    @NotBlank(message = "学号不能为空")
    private String studentId;

    @NotBlank(message = "密保答案不能为空")
    private String securityAnswer;

    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 64, message = "密码不少于 6 位")
    private String newPassword;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
