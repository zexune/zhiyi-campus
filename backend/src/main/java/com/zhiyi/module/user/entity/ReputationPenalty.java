package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 独立信誉处罚记录：确认违规时创建，不修改任何真实交易评价。
 */
@Data
@TableName("reputation_penalty")
public class ReputationPenalty {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Long userId;
    private Long adminId;
    private String type;
    private Integer points;
    private String reason;
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    private LocalDateTime revokedAt;
}
