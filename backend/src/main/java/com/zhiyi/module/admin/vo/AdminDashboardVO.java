package com.zhiyi.module.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超管数据大盘返回体
 */
@Data
public class AdminDashboardVO {
    /** 用户总数 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long totalUsers;
    /** 在售商品数 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long onSaleItems;
    /** 今日交易总额 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private String todayTradeAmount;
    /** 待审核违规数 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private long pendingViolations;

    /** 最近 5 条待审核违规记录 */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<RecentViolation> recentViolations;

    /** 近 7 日交易趋势（每日完成订单数） */
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TradeTrendPoint> trend;

    @Data
    public static class RecentViolation {
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private Long id;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private Long userId;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String reporterName;    // 发布者昵称
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String originalTitle;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String violationType;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String violationReason;
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private LocalDateTime createdAt;
    }

    @Data
    public static class TradeTrendPoint {
        /** 日期，格式 yyyy-MM-dd */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private String date;
        /** 当日完成订单数 */
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        private long count;
        /** 当日交易总额 */
        private String totalAmount;
    }
}
