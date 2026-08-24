package com.zhiyi.module.item.vo;

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
    private List<ItemCardVO> records;
    private String nextCursor;
    private boolean hasMore;
    /** 首屏估算值；游标翻页时复返签发时的估算，不冒充跨页精确 total */
    private long estimatedTotal;
}
