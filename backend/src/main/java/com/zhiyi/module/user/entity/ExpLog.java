package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 经验值变动记录 —— 模块一成长体系（谁、加/减多少、原因、变动后经验与等级）
 */
@Data
@TableName("exp_log")
public class ExpLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Integer delta;          // 变动量（+50 完成订单 / -30 违规下架）
    private Integer expAfter;       // 变动后累计经验
    private Integer levelAfter;     // 变动后等级
    private String reason;          // 变动原因

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
