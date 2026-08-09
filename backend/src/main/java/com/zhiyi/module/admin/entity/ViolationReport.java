package com.zhiyi.module.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("violation_report")
public class ViolationReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** USER_REPORT 时为举报人，其余来源为空。 */
    private Long reporterId;
    private String originalTitle;
    private String originalDescription;
    /** LOCAL_RULE / USER_REPORT / CORRECTION */
    private String source;
    private String violationType;
    private String violationReason;
    /** 命中的本地规则编号（JSON 数组）。 */
    private String matchedRules;
    private String ruleVersion;
    private Long itemId;
    private String status;          // PENDING / CONFIRMED / DISMISSED / OVERTURNED
    private Long handlerId;
    private String handleNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime handledAt;
}
