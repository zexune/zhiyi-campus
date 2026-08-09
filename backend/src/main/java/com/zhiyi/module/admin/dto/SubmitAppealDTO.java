package com.zhiyi.module.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitAppealDTO(
        @NotBlank(message = "申诉理由不能为空")
        @Size(min = 10, max = 500, message = "申诉理由需为 10-500 字")
        String reason
) {
}
