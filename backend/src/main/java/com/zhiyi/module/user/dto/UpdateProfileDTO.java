package com.zhiyi.module.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新个人资料（需求 1.1 昵称可修改 / B.1 PUT /api/user/profile）
 */
@Data
public class UpdateProfileDTO {

    @Size(min = 1, max = 50, message = "昵称须为 1-50 字")
    private String nickname;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /** 所属学校可随转学、升学等情况修改；传入时必须是启用中的学校。 */
    private Long schoolId;

    /** 学校邮箱与手机号一样直接保存；非空时必须匹配所选学校的邮箱后缀。 */
    @Size(max = 100, message = "邮箱最长 100 字")
    @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+$", message = "邮箱格式不正确")
    private String schoolEmail;

    // ---- 模块一创新功能 A4：自愿补全的信任资料（全部可选）----
    @Size(max = 50, message = "校区最长 50 字")
    private String campus;

    @Size(max = 50, message = "学院最长 50 字")
    private String college;

    @Size(max = 10, message = "年级最长 10 字")
    private String grade;

    @Size(max = 50, message = "宿舍楼最长 50 字")
    private String dormitory;
}
