package com.zhiyi.module.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 交易热力图数据点（D5）
 */
@Data
@AllArgsConstructor
public class TradeHeatmapVO {
    /** 交易地点 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String location;
    /** 该地点的交易次数 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long count;
}
