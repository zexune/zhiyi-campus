package com.zhiyi.module.item.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

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
    /** 商品标签筛选：零到多个，任一命中即属于专题；服务端会做规范化去重与限量 */
    @Size(max = 6, message = "筛选标签最多6个")
    private List<@NotBlank(message = "筛选标签不能为空白") @Size(max = 12, message = "筛选标签需为2-12字") String> filterTags;
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
