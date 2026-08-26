package com.zhiyi.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 游标式商品列表响应。
 *
 * total 为首屏估算值（estimated），跨页不保证精确；
 * hasMore 表示同游标链还有下一页；nextCursor 为空表示链结束。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MarketplaceFeedVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<ItemSummaryResponse> records;
    /** 链结束时显式为 null；其余页面为不透明签名游标。 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true)
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.ALWAYS)
    private String nextCursor;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean hasMore;
    /** 首屏估算值；游标翻页时复返签发时的估算，不冒充跨页精确 total */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long estimatedTotal;
}
