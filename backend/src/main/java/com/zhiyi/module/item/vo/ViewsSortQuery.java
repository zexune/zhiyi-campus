package com.zhiyi.module.item.vo;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/** views 排序 keyset 查询参数（脚本 SQL 绑定用）。 */
@Data
@Builder
public class ViewsSortQuery {
    private Long schoolId;
    private String keyword;
    private Long categoryId;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String type;
    private List<String> tags;
    private long snapshotMaxItemId;
    private long snapshotMaxRevision;
    /** null 表示首屏（无 keyset 边界） */
    private Long cursorViewCount;
    private Long cursorItemId;
    private int limit;
}
