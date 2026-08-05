package com.zhiyi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求（需求 1.1）
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "学号不能为空")
    @Pattern(regexp = "^[A-Za-z0-9]{4,20}$", message = "学号须为 4-20 位字母或数字")
    private String studentId;

    /** 所属学校（模块一创新功能，注册必填） */
    @NotNull(message = "请选择所属学校")
    private Long schoolId;

    /**
     * 学校邮箱（可选），无需验证码；填写时后缀须与所选学校匹配。
     */
    @Size(max = 100, message = "邮箱最长 100 字")
    @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+$", message = "邮箱格式不正确")
    private String schoolEmail;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码不少于 6 位")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;

    /** 昵称可空，默认生成「同学_学号后4位」 */
    @Size(max = 50, message = "昵称最长 50 字")
    private String nickname;

    /** 密保问题：可从预设列表选择，也支持用户自定义 */
    @NotBlank(message = "请填写密保问题")
    @Size(max = 50, message = "密保问题最长 50 字")
    private String securityQuestion;

    @NotBlank(message = "密保答案不能为空")
    @Size(max = 100, message = "密保答案最长 100 字")
    private String securityAnswer;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;
}
