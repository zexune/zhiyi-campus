package com.zhiyi.module.admin.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 交易热力图数据点（D5）
 */
@Data
@AllArgsConstructor
public class TradeHeatmapVO {
    /** 交易地点 */
    private String location;
    /** 该地点的交易次数 */
    private long count;
}
