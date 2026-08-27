package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String studentId;
    private String password;
    private String nickname;
    /** 自定义头像 URL（/uploads/avatars/…；null 表示未设置，前端回退文字头像） */
    private String avatar;
    private String phone;
    // ---- 模块一创新功能：学校归属 + 学校邮箱 ----
    private Long schoolId;          // 所属学校（普通功能按学校隔离；管理员默认上海大学）
    private String schoolEmail;     // 学校邮箱（可选）
    // ---- 模块一创新功能：信任标签（自愿补全）----
    private String campus;          // 校区
    private String college;         // 学院
    private String grade;           // 年级
    private String dormitory;       // 宿舍楼
    private UserRole role;
    private UserStatus status;
    private LocalDateTime banUntilTime;
    /**
     * Token 版本：重置密码、改密、封禁或注销时原子递增，旧版本 JWT 一律拒绝。
     */
    private Integer tokenVersion;
    /** 资料乐观并发版本：仅资料修改推进，与钱包/状态/Token 写入无关。 */
    private Long profileVersion;
    /** SYSTEM 技术主体标记（不可登录、不可交易；全库恰好一个）。 */
    private Boolean isSystem;
    private Integer level;
    private Integer exp;
    private BigDecimal walletBalance;
    private String securityQuestion;
    private String securityAnswer;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
