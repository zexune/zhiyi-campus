package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 经验值变动记录 —— 模块一成长体系（谁、加/减多少、原因、变动后经验与等级）
 */
@Data
@TableName("exp_log")
public class ExpLog {
    @TableId(type = IdType.AUTO)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    private Long userId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer delta;          // 经验值变动量（例如 +50 完成订单）
    private Integer expAfter;       // 变动后累计经验
    private Integer levelAfter;     // 变动后等级
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;          // 变动原因
    /** 经验事件目录编码（ExpRule.name()；历史/系统行可为空） */
    private String ruleCode;
    /** 一次性任务去重键（uk_exp_dedup；可重复来源为空） */
    private String dedupKey;

    @TableField(fill = FieldFill.INSERT)
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
}
