package com.zhiyi.module.item.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 浏览量刷新批次凭据：flush_id 幂等，防重复累计。 */
@Data
@TableName("view_flush")
public class ViewFlush {
    @TableId(type = IdType.INPUT)
    private String flushId;
    private Integer itemCount;
    private LocalDateTime flushedAt;
}
