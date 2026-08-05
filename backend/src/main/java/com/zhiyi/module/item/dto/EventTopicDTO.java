package com.zhiyi.module.item.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EventTopicDTO {
    @NotBlank(message = "专题名称不能为空")
    @Size(max = 100, message = "专题名称不能超过100字")
    private String title;
    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;
    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;
    @Pattern(regexp = "SELL|BUY|SWAP|ERRAND", message = "筛选类型不合法")
    private String filterType;
    private Long filterCategoryId;
    @Size(max = 50, message = "筛选标签不能超过50字")
    private String filterTag;
    @NotBlank(message = "Banner 文案不能为空")
    @Size(max = 255, message = "Banner 文案不能超过255字")
    private String bannerText;
    @NotNull(message = "启用状态不能为空")
    private Boolean enabled;

    @AssertTrue(message = "专题结束时间必须晚于开始时间")
    public boolean isTimeRangeValid() {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }
}
