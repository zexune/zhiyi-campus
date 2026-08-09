package com.zhiyi.module.admin.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.enums.ViolationSource;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.common.mybatis.StringListJsonTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "violation_report", autoResultMap = true)
public class ViolationReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    /** USER_REPORT 时为举报人，其余来源为空。 */
    private Long reporterId;
    private String originalTitle;
    private String originalDescription;
    /** LOCAL_RULE / USER_REPORT / CORRECTION */
    private ViolationSource source;
    private String violationType;
    private String violationReason;
    /** 命中的本地规则编号（JSON 数组）。 */
    @TableField(typeHandler = StringListJsonTypeHandler.class)
    private List<String> matchedRules;
    private String ruleVersion;
    private Long itemId;
    private ViolationStatus status;
    private Long handlerId;
    private String handleNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime handledAt;
}
