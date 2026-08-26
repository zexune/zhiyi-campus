package com.zhiyi.module.user.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.support.LevelRule;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 用户信息视图 —— 不暴露密码/密保等敏感字段
 *
 * 必填集 = 登录摘要（id/studentId/nickname/role）∪ 管理端列表行（status/banUntilTime/
 * 学校归属列）；campus 等可选资料与经验进度字段随视图填充，保持可选。
 */
@Data
public class UserVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String studentId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String nickname;
    /** 未绑定时序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String phone;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String role;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String status;
    /** 未封禁时序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private LocalDateTime banUntilTime;
    /** 资料乐观并发版本：编辑资料请求必须携带并匹配当前版本 */
    private Long profileVersion;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer level;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String levelTitle;
    private Integer exp;
    /** 当前等级起点经验（前端进度条起点） */
    private Integer currentLevelBaseExp;
    /** 下一级所需累计经验；满级为 null */
    private Integer nextLevelExp;
    private BigDecimal walletBalance;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private LocalDateTime createdAt;
    // ---- 模块一创新功能：学校归属 + 学校邮箱 + 信任标签 ----
    /** 未选择学校（如管理员）时序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Long schoolId;
    /** 学校名称（由 Service 关联 school 表填充；仅 schoolId 已知时可为 null） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String schoolName;
    /** 未认证学校邮箱时序列化为显式 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
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
        vo.setRole(u.getRole().code());
        vo.setStatus(u.getStatus().code());
        vo.setBanUntilTime(u.getBanUntilTime());
        vo.setProfileVersion(u.getProfileVersion());
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
