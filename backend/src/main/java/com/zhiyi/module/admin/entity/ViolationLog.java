package com.zhiyi.module.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("violation_log")
public class ViolationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;            // 被处罚用户
    private Long adminId;           // 操作管理员
    private String type;            // WARNING / BAN_TEMP / BAN_PERM
    private String reason;
    private Integer banDays;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
