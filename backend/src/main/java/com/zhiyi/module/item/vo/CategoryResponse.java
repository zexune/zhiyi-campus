package com.zhiyi.module.item.vo;

import com.zhiyi.module.item.entity.Category;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/**
 * 分类对外响应（P8：Controller 不再直返 .entity 包类型）。
 * 字段与旧 Category 实体序列化保持一致，前端 wire 兼容。
 */
@Schema(description = "商品分类")
public record CategoryResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Long id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
        String icon,
        Integer sortOrder,
        LocalDateTime createdAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(),
                category.getIcon(), category.getSortOrder(), category.getCreatedAt());
    }
}
