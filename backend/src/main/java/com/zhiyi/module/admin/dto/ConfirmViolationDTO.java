package com.zhiyi.module.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 确认违规 + 处罚请求
 */
@Data
public class ConfirmViolationDTO {

    /** WARNING 警告 / BAN_TEMP 限时封禁 / BAN_PERM 永久封禁 */
    @NotBlank(message = "处罚类型不能为空")
    private String type;

    @NotBlank(message = "处罚原因不能为空")
    @Size(max = 500, message = "处罚原因最长 500 字")
    private String reason;

    /** 限时封禁天数（type = BAN_TEMP 时必填，1-365） */
    @Min(value = 1, message = "封禁天数最少为1天")
    @Max(value = 365, message = "封禁天数最多为365天")
    private Integer banDays;

    /** 处理备注 */
    @Size(max = 500, message = "处理备注最长 500 字")
    private String handleNote;
}
