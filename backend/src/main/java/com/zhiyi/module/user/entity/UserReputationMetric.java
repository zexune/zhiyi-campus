package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 响应速度固定大小汇总指标：公开信誉接口只读本行，不回退全量扫描。 */
@Data
@TableName("user_reputation_metric")
public class UserReputationMetric {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private Integer sampleCount;
    private Long totalGapSeconds;
    private LocalDateTime updatedAt;
}
