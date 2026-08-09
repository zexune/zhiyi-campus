package com.zhiyi.module.item.vo;

public record TagAggregateRow(
        Long categoryId,
        String categoryName,
        Integer categorySort,
        String tagName,
        Long itemCount
) {
}
