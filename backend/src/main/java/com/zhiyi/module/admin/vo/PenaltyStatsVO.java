package com.zhiyi.module.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 用户处罚评分统计（D4：独立信誉处罚）
 * 供管理端查询违规次数及独立处罚记录计算出的合规度
 */
@Data
public class PenaltyStatsVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    /** 累计违规确认次数 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long confirmedViolations;
    /** 警告次数 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long warningCount;
    /** 封禁次数（临时+永久） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long banCount;
    /** 独立处罚折算后的合规度（0-100，越高越合规） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private int penaltyScore;
}
