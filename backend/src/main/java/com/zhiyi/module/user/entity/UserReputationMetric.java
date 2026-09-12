package com.zhiyi.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 响应速度固定大小汇总指标：公开信誉接口只读本行，不回退全量扫描。
 *
 * ewmaGapSeconds 是首响间隔的指数加权移动平均（EWMA，α=0.1）：
 * 新样本占 10% 权重，旧样本指数淡出——分数反映近期表现，一次灾难性
 * 慢回复不再被几百次历史快回复永久稀释。明细可由 chat_response_sample
 * 按主键游标离线回填，未回填期间按样本不足语义展示。
 */
@Data
@TableName("user_reputation_metric")
public class UserReputationMetric {
    @TableId(type = IdType.INPUT)
    private Long userId;
    private Integer sampleCount;
    /** 首响间隔 EWMA（秒） */
    private Long ewmaGapSeconds;
    private LocalDateTime lastSampleAt;
    private LocalDateTime updatedAt;
}
