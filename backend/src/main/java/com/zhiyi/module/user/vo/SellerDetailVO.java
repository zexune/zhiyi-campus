package com.zhiyi.module.user.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录用户可查看的卖家档案。
 *
 * 仅包含商品详情弹窗需要展示的联系与校园资料，不包含学号、钱包、
 * 账号状态、密保等敏感字段。
 */
@Data
@AllArgsConstructor
public class SellerDetailVO {
    private Long id;
    private String nickname;
    private Integer level;
    private String levelTitle;
    private String schoolName;
    private String phone;
    private String schoolEmail;
    private String campus;
    private String college;
    private String grade;
    private String dormitory;
}
