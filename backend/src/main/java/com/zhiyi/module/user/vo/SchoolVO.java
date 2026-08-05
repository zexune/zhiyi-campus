package com.zhiyi.module.user.vo;

import com.zhiyi.module.user.entity.School;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 学校下拉选项 —— 注册页和个人资料页公开接口用，只暴露必要字段
 */
@Data
@AllArgsConstructor
public class SchoolVO {
    private Long id;
    private String name;
    private String code;
    /** 邮箱后缀（前端提示及提交前校验用） */
    private String emailDomain;
    /** 状态：ACTIVE / DISABLED */
    private String status;

    public static SchoolVO from(School s) {
        return new SchoolVO(s.getId(), s.getName(), s.getCode(), s.getEmailDomain(), s.getStatus());
    }
}
