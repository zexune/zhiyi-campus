package com.zhiyi.module.item.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 收藏/取消收藏后的即时状态。
 */
@Data
@AllArgsConstructor
public class FavoriteToggleVO {
    private Long itemId;
    private boolean favorite;
    private Long favoriteCount;
}
