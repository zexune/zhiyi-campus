package com.zhiyi.common.enums;

import java.util.Locale;

/**
 * “我的发布”筛选状态。REVIEWING 是由内容审核状态派生的展示状态，
 * 其余值映射到商品持久化状态。
 */
public enum ItemListStatus {
    ON_SALE(ItemStatus.ON_SALE),
    REVIEWING(null),
    SOLD(ItemStatus.SOLD),
    OFF_SHELF(ItemStatus.OFF_SHELF);

    private final ItemStatus persistedStatus;

    ItemListStatus(ItemStatus persistedStatus) {
        this.persistedStatus = persistedStatus;
    }

    public ItemStatus persistedStatus() {
        return persistedStatus;
    }

    public static ItemListStatus from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("item list status is blank");
        }
        return valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
