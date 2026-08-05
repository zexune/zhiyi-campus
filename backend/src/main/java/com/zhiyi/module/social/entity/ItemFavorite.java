package com.zhiyi.module.social.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

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
