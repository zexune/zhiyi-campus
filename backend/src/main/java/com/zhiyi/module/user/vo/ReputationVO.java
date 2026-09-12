package com.zhiyi.module.user.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 信誉雷达六维明细（A6）—— 每维携带分值、样本量与一句话归因。
 *
 * score 为 null 表示样本不足（低于最小样本门槛）：前端该维画中心点并标注
 * "样本不足"，与"表现差"从视觉与语义上彻底区分；不再用固定基线冒充分值。
 *
 * 维度数据来源（详见 ReputationService）：
 * completionRate —— 近 180 天订单完成率（卖家侧）
 * responseSpeed  —— 首响间隔 EWMA（α=0.1）
 * accuracy       —— 近 180 天评价描述相符占比
 * praise         —— 近 180 天评价平均星级折算
 * activity       —— 近 30 天发布与成交加权计数
 * compliance     —— 有效处罚按配置窗口（zhiyi.moderation.penalty-decay-days）线性衰减后的合规度
 */
@Data
@AllArgsConstructor
public class ReputationVO {
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    /** 固定六维顺序（与前端 REPUTATION_DIMENSIONS 一致） */
    private List<DimensionVO> dimensions;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class DimensionVO {
        /** 维度键（completionRate/responseSpeed/accuracy/praise/activity/compliance） */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String key;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String label;
        /** 0-100 分值；null = 样本不足 */
        private Integer score;
        /** 参与本维计算的样本量（订单数/评价数/处罚数等） */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private int samples;
        /** 一句话归因（前端 hover 展示，解释"为什么是这个分"） */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String summary;
    }
}
