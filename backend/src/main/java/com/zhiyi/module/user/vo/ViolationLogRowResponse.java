package com.zhiyi.module.user.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 处罚记录行（P0-4 命名 DTO）：替代曾经的 Map&lt;String,Object&gt; 弱类型响应。
 * 字段可空性契约：
 * - banDays：永久封禁（BAN_PERM）时为 null，限时封禁为正整数；
 * - studentId/nickname：被处罚用户已删除（逻辑删除）时为 null。
 */
@Data
public class ViolationLogRowResponse {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    /** 处罚动作：BAN_TEMP 限时封禁 / BAN_PERM 永久封禁 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = {"BAN_TEMP", "BAN_PERM"})
    private String type;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String reason;
    /** 永久封禁时为 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private Integer banDays;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDateTime createdAt;
    /** 用户已删除时为 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String studentId;
    /** 用户已删除时为 null */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @JsonInclude(JsonInclude.Include.ALWAYS)
    private String nickname;
}
