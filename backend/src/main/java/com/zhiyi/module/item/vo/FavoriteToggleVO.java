package com.zhiyi.module.item.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 收藏/取消收藏后的即时状态。
 */
@Data
@AllArgsConstructor
public class FavoriteToggleVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long itemId;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private boolean favorite;
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long favoriteCount;
}
