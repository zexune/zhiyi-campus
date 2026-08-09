package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.zhiyi.common.enums.SchoolStatus;
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
    private SchoolStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
