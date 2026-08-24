package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 登录/密保失败限流行。attempt_key 带场景前缀（login/reset/admin/chpw）；
 * 全部时间判断使用数据库 CURRENT_TIMESTAMP(6)，多实例天然一致。
 */
@Data
@TableName("login_attempt")
public class LoginAttempt {
    @TableId(type = IdType.INPUT)
    private String attemptKey;
    private LocalDateTime windowStartedAt;
    private Integer failCount;
    private LocalDateTime lockedUntil;
    private LocalDateTime updatedAt;
}
