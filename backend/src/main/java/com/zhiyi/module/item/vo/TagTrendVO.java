package com.zhiyi.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

public record TagTrendVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String tag,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long count) {
}
