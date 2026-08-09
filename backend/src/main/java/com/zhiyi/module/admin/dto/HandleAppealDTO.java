package com.zhiyi.module.admin.dto;

import jakarta.validation.constraints.Size;

public record HandleAppealDTO(
        @Size(max = 500, message = "处理说明最长 500 字")
        String handleNote
) {
}
