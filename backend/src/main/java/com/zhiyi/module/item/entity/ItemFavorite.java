package com.zhiyi.module.item.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/** 商品收藏关系：领域归属商品模块（收藏是商品与用户的关系，非聊天社交）。 */
@Data
@TableName("item_favorite")
public class ItemFavorite {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long itemId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
