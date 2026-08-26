package com.zhiyi.module.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 解封 / 恢复注销账户请求（替代曾经的 Map&lt;String,Long&gt; 弱类型请求体）。 */
@Data
public class UnbanUserDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;
}
