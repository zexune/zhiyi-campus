package com.zhiyi.module.social.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ChatStartDTO {
    @NotNull(message = "商品ID不能为空")
    private Long itemId;
}
