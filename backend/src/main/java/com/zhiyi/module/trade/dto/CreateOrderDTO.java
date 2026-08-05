package com.zhiyi.module.trade.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 创建订单请求体
 */
@Data
public class CreateOrderDTO {

    @NotNull(message = "商品ID不能为空")
    private Long itemId;
}
