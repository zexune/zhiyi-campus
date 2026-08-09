package com.zhiyi.module.admin.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("violation_appeal")
public class ViolationAppeal {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long itemId;
    private Long userId;
    private String reason;
    private String status;          // PENDING / APPROVED / REJECTED
    private Long handlerId;
    private String handleNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime handledAt;
}
