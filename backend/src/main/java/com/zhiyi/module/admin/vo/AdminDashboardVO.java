package com.zhiyi.module.admin.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 超管数据大盘返回体
 */
@Data
public class AdminDashboardVO {
    /** 用户总数 */
    private long totalUsers;
    /** 在售商品数 */
    private long onSaleItems;
    /** 今日交易总额 */
    private String todayTradeAmount;
    /** 待审核违规数 */
    private long pendingViolations;

    /** 最近 5 条待审核违规记录 */
    private List<RecentViolation> recentViolations;

    /** 近 7 日交易趋势（每日完成订单数） */
    private List<TradeTrendPoint> trend;

    @Data
    public static class RecentViolation {
        private Long id;
        private Long userId;
        private String reporterName;    // 发布者昵称
        private String originalTitle;
        private String violationType;
        private String violationReason;
        private LocalDateTime createdAt;
    }

    @Data
    public static class TradeTrendPoint {
        /** 日期，格式 yyyy-MM-dd */
        private String date;
        /** 当日完成订单数 */
        private long count;
        /** 当日交易总额 */
        private String totalAmount;
    }
}
