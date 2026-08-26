package com.zhiyi.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

public record TagCountVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
}
