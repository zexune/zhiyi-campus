package com.zhiyi.module.social.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ChatItemSummaryVO {
    private Long id;
    private String title;
    private BigDecimal price;
    private String coverImage;
    private String status;
}
