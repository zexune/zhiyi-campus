package com.zhiyi.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 确认内容违规并执行固定警告扣分。
 */
@Data
public class ConfirmViolationDTO {

    @NotBlank(message = "处罚原因不能为空")
    @Size(max = 500, message = "处罚原因最长 500 字")
    private String reason;

    /** 处理备注 */
    @Size(max = 500, message = "处理备注最长 500 字")
    private String handleNote;
}
