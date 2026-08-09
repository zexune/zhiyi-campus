package com.zhiyi.module.item.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReportItemDTO(
        @NotBlank(message = "举报类型不能为空")
        @Pattern(regexp = "PRICE_FRAUD|PROHIBITED_ITEM|IMAGE_VIOLATION|ADVERTISING|OTHER",
                message = "举报类型不受支持")
        String type,
        @Size(max = 500, message = "举报说明最长 500 字")
        String details
) {
    @AssertTrue(message = "选择其他类型时必须填写举报说明")
    public boolean isDetailsValid() {
        return !"OTHER".equals(type) || (details != null && !details.isBlank());
    }
}
