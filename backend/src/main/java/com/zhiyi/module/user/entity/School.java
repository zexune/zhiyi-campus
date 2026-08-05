package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("school")
public class School {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;            // 上海大学
    private String code;            // SHU
    private String emailDomain;     // @shu.edu.cn
    private String status;          // ACTIVE / DISABLED

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
