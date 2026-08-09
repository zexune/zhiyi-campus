package com.zhiyi.module.item.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tag")
public class Tag {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String normalizedName;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
