package com.zhiyi.module.admin.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 超管商品检索结果 —— 4.7 内容管理（强制下架选择商品用）
 */
@Data
public class AdminItemVO {
    private Long id;
    private String title;
    private String type;            // SELL / BUY
    private BigDecimal price;
    private String status;          // ON_SALE / PENDING / SOLD / OFF_SHELF
    private Long publisherId;
    private String publisherNickname;
    private LocalDateTime createdAt;
}
