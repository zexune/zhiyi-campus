package com.zhiyi.module.user.vo;

import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.support.LevelRule;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户信息视图 —— 不暴露密码/密保等敏感字段
 */
@Data
public class UserVO {
    private Long id;
    private String studentId;
    private String nickname;
    private String phone;
    private String role;
    private String status;
    private LocalDateTime banUntilTime;
    private Integer level;
    private String levelTitle;
    private Integer exp;
    /** 当前等级起点经验（前端进度条起点） */
    private Integer currentLevelBaseExp;
    /** 下一级所需累计经验；满级为 null */
    private Integer nextLevelExp;
    private BigDecimal walletBalance;
    private LocalDateTime createdAt;
    // ---- 模块一创新功能：学校归属 + 学校邮箱 + 信任标签 ----
    private Long schoolId;
    /** 学校名称（由 Service 关联 school 表填充；仅 schoolId 已知时可为 null） */
    private String schoolName;
    private String schoolEmail;
    private String campus;
    private String college;
    private String grade;
    private String dormitory;

    public static UserVO from(SysUser u) {
        return from(u, null);
    }

    /** 带学校名称的构建（schoolName 由调用方从 school 表关联查得） */
    public static UserVO from(SysUser u, String schoolName) {
        UserVO vo = new UserVO();
        vo.setId(u.getId());
        vo.setStudentId(u.getStudentId());
        vo.setNickname(u.getNickname());
        vo.setPhone(u.getPhone());
        vo.setRole(u.getRole());
        vo.setStatus(u.getStatus());
        vo.setBanUntilTime(u.getBanUntilTime());
        vo.setLevel(u.getLevel());
        vo.setLevelTitle(LevelRule.titleOf(u.getLevel()));
        vo.setExp(u.getExp());
        vo.setCurrentLevelBaseExp(LevelRule.currentLevelBaseExp(u.getLevel()));
        vo.setNextLevelExp(LevelRule.nextLevelExp(u.getLevel()));
        vo.setWalletBalance(u.getWalletBalance());
        vo.setCreatedAt(u.getCreatedAt());
        vo.setSchoolId(u.getSchoolId());
        vo.setSchoolName(schoolName);
        vo.setSchoolEmail(u.getSchoolEmail());
        vo.setCampus(u.getCampus());
        vo.setCollege(u.getCollege());
        vo.setGrade(u.getGrade());
        vo.setDormitory(u.getDormitory());
        return vo;
    }
}
