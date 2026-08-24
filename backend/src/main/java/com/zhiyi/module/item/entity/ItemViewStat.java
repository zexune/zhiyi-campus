package com.zhiyi.module.item.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 商品浏览量派生统计（独立于 item 业务行，只增不减）。 */
@Data
@TableName("item_view_stat")
public class ItemViewStat {
    @TableId(type = IdType.INPUT)
    private Long itemId;
    private Long viewCount;
    private LocalDateTime updatedAt;
}
