package com.zhiyi.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 学校创建/更新请求 DTO（超管后台 D1）
 */
@Data
public class SchoolDTO {

    @NotBlank(message = "学校名称不能为空")
    @Size(max = 100, message = "学校名称最长100字")
    private String name;

    @NotBlank(message = "学校代码不能为空")
    @Size(max = 20, message = "学校代码最长20字")
    private String code;

    /** 邮箱域名后缀，如 @stu.shu.edu.cn */
    @Size(max = 100, message = "邮箱域名最长100字")
    private String emailDomain;

    /** ACTIVE / DISABLED */
    private String status;
}
