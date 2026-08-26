package com.zhiyi.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record TagGroupVO(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long categoryId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String categoryName,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<TagCountVO> tags) {
}
