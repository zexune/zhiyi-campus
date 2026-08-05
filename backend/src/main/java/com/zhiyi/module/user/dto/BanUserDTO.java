package com.zhiyi.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员处罚请求（需求 1.6 多级违规与封禁）
 */
@Data
public class BanUserDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /** WARNING 警告 / BAN_TEMP 限时封禁 / BAN_PERM 永久封禁 */
    @NotBlank(message = "处罚类型不能为空")
    private String type;

    @NotBlank(message = "处罚原因不能为空")
    @Size(max = 500, message = "处罚原因最长 500 字")
    private String reason;

    /** 限时封禁天数（type = BAN_TEMP 时必填，1-365） */
    private Integer banDays;
}
