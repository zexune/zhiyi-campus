package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.user.entity.UserReputationMetric;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.mapper.UserReputationMetricMapper;
import com.zhiyi.module.user.vo.ReputationVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 信誉雷达聚合服务（A6）—— 六维各带样本量与归因的透明账本。
 *
 * 口径统一（B10 后续演进）：
 * - 比率类维度（完成率/准确/好评）与响应速度统一近 180 天窗口或指数衰减，
 *   分数反映近期表现：行为改善可见（信用修复），历史包袱会自然淡出；
 * - 最小样本门槛 MIN_SAMPLES：不足时不给分（score=null，"样本不足"语义），
 *   杜绝"一次 3 星好评直接打 60"的小样本噪声直达视觉；
 * - 固定成本读取：响应速度只读 user_reputation_metric 单行（EWMA 增量维护），
 *   评价维度单行聚合 SQL（COUNT/AVG/SUM），不把全量评价装载进内存。
 *
 * 派生统计不得成为交易、鉴权或处罚的权威来源；历史数据回填按主键游标
 * 离线分批执行，未回填完成期间按样本不足展示，禁止回退到在线全量扫描。
 */
@Service
@RequiredArgsConstructor
public class ReputationService {

    /** 比率类维度的最小样本门槛：低于此值该维不给出分 */
    public static final int MIN_SAMPLES = 5;
    /** 比率类维度统计窗口（天） */
    private static final int RATIO_WINDOW_DAYS = 180;
    /** 活跃度统计窗口（天） */
    private static final int ACTIVITY_WINDOW_DAYS = 30;
    /** 响应速度满分线（分钟）与地板线（分钟） */
    private static final double RESPONSE_FULL_MINUTES = 5;
    private static final double RESPONSE_FLOOR_MINUTES = 720;
    private static final int RESPONSE_FLOOR_SCORE = 20;

    private final TradeOrderMapper orderMapper;
    private final TradeReviewMapper reviewMapper;
    private final ItemMapper itemMapper;
    private final SysUserMapper userMapper;
    private final UserReputationMetricMapper metricMapper;
    private final ReputationPenaltyService penaltyService;

    public ReputationVO compute(Long userId) {
        if (userId == null || userMapper.selectById(userId) == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        // 评价聚合一次复用：准确度与好评两个维度共用同一行 COUNT/AVG/SUM 结果
        ReviewAggregate reviews = reviewAggregate(userId);
        List<ReputationVO.DimensionVO> dimensions = List.of(
                completionRate(userId),
                responseSpeed(userId),
                accuracyDimension(reviews),
                praiseDimension(reviews),
                activity(userId),
                compliance(userId));
        return new ReputationVO(userId, dimensions);
    }

    // ================================================================
    // 交易完成率：近 180 天卖家侧 completed / (completed + cancelled)
    // ================================================================

    private ReputationVO.DimensionVO completionRate(Long userId) {
        LocalDateTime windowStart = windowStart(RATIO_WINDOW_DAYS);
        long completed = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.COMPLETED)
                .ge(TradeOrder::getCompletedAt, windowStart));
        long cancelled = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.CANCELLED)
                .ge(TradeOrder::getCancelledAt, windowStart));
        long total = completed + cancelled;
        if (total < MIN_SAMPLES) {
            return dimension("completionRate", "交易完成率", null, (int) total,
                    "近 " + RATIO_WINDOW_DAYS + " 天成交记录不足 " + MIN_SAMPLES + " 单，暂不评分");
        }
        int score = clamp((int) Math.round(completed * 100.0 / total));
        return dimension("completionRate", "交易完成率", score, (int) total,
                "近 " + RATIO_WINDOW_DAYS + " 天 " + total + " 单成交 " + completed + " 单、取消 " + cancelled + " 单");
    }

    // ================================================================
    // 响应速度：首响间隔 EWMA（α=0.1），样本不足不评分
    // ================================================================

    private ReputationVO.DimensionVO responseSpeed(Long userId) {
        UserReputationMetric metric = metricMapper.selectById(userId);
        int samples = metric == null || metric.getSampleCount() == null ? 0 : metric.getSampleCount();
        if (metric == null || samples < MIN_SAMPLES) {
            return dimension("responseSpeed", "响应速度", null, samples,
                    "买家首响样本不足 " + MIN_SAMPLES + " 条，暂不评分");
        }
        long ewmaSeconds = metric.getEwmaGapSeconds() == null ? 0 : metric.getEwmaGapSeconds();
        double avgMinutes = (ewmaSeconds / 60.0);
        int score;
        if (avgMinutes <= RESPONSE_FULL_MINUTES) {
            score = 100;
        } else {
            score = (int) Math.round(100 - (avgMinutes - RESPONSE_FULL_MINUTES)
                    / (RESPONSE_FLOOR_MINUTES - RESPONSE_FULL_MINUTES) * (100 - RESPONSE_FLOOR_SCORE));
            score = Math.max(score, RESPONSE_FLOOR_SCORE);
        }
        return dimension("responseSpeed", "响应速度", clamp(score), samples,
                "综合近 " + samples + " 次首响（新样本权重更高），加权平均约 " + humanMinutes(avgMinutes));
    }

    // ================================================================
    // 描述准确 / 历史好评：近 180 天评价单行聚合
    // ================================================================

    private ReputationVO.DimensionVO accuracyDimension(ReviewAggregate reviews) {
        if (reviews.total() < MIN_SAMPLES) {
            return dimension("accuracy", "描述准确度", null, (int) reviews.total(),
                    "近 " + RATIO_WINDOW_DAYS + " 天评价不足 " + MIN_SAMPLES + " 条，暂不评分");
        }
        int score = clamp((int) Math.round(reviews.accurateTotal() * 100.0 / reviews.total()));
        return dimension("accuracy", "描述准确度", score, (int) reviews.total(),
                "近 " + RATIO_WINDOW_DAYS + " 天 " + reviews.total() + " 条评价，"
                        + reviews.accurateTotal() + " 条描述相符");
    }

    private ReputationVO.DimensionVO praiseDimension(ReviewAggregate reviews) {
        if (reviews.total() < MIN_SAMPLES) {
            return dimension("praise", "历史好评", null, (int) reviews.total(),
                    "近 " + RATIO_WINDOW_DAYS + " 天评价不足 " + MIN_SAMPLES + " 条，暂不评分");
        }
        int score = clamp((int) Math.round(reviews.avgRating() / 5.0 * 100));
        return dimension("praise", "历史好评", score, (int) reviews.total(),
                "近 " + RATIO_WINDOW_DAYS + " 天 " + reviews.total() + " 条评价，平均 "
                        + String.format("%.1f", reviews.avgRating()) + " 星");
    }

    /** 评价聚合（单行聚合 SQL）：窗口内总数、平均星级、描述准确数。 */
    private ReviewAggregate reviewAggregate(Long userId) {
        List<Map<String, Object>> rows = reviewMapper.selectMaps(new QueryWrapper<TradeReview>()
                .select("COUNT(*) AS total",
                        "COALESCE(AVG(rating), 0) AS avg_rating",
                        "COALESCE(SUM(accurate), 0) AS accurate_total")
                .eq("target_id", userId)
                .ge("created_at", windowStart(RATIO_WINDOW_DAYS)));
        if (rows.isEmpty() || rows.getFirst() == null) {
            return new ReviewAggregate(0, 0, 0);
        }
        Map<String, Object> row = rows.getFirst();
        return new ReviewAggregate(
                toLong(row.get("total")),
                toLong(row.get("accurate_total")),
                toDouble(row.get("avg_rating")));
    }

    // ================================================================
    // 活跃度：近 30 天发布 ×2 + 成交 ×5，封顶 100（精确计数，不设样本门槛）
    // ================================================================

    private ReputationVO.DimensionVO activity(Long userId) {
        LocalDateTime since = windowStart(ACTIVITY_WINDOW_DAYS);
        long published = itemMapper.selectCount(new LambdaQueryWrapper<Item>()
                .eq(Item::getPublisherId, userId)
                .ge(Item::getCreatedAt, since));
        long traded = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.COMPLETED)
                .ge(TradeOrder::getCompletedAt, since));
        int score = clamp((int) Math.min(100, published * 2 + traded * 5));
        return dimension("activity", "活跃度", score, (int) (published + traded),
                "近 " + ACTIVITY_WINDOW_DAYS + " 天发布 " + published + " 件、成交 " + traded + " 单");
    }

    // ================================================================
    // 合规度：有效处罚按创建时间线性衰减（窗口取 ReputationPenaltyService 配置），
    // 满窗口自然失效
    // ================================================================

    private ReputationVO.DimensionVO compliance(Long userId) {
        List<ReputationPenaltyService.EffectivePenalty> penalties =
                penaltyService.activePenaltiesWithDecay(userId);
        if (penalties.isEmpty()) {
            return dimension("compliance", "合规度", 100, 0, "无有效处罚记录");
        }
        int deducted = (int) Math.round(penalties.stream()
                .mapToDouble(ReputationPenaltyService.EffectivePenalty::effectivePoints).sum());
        int score = clamp(100 - deducted);
        // 窗口天数引用实际配置值，与其他维度的"近 N 天"文案口径一致
        return dimension("compliance", "合规度", score, penalties.size(),
                penalties.size() + " 条有效处罚（按 " + penaltyService.penaltyDecayDays()
                        + " 天线性衰减），当前累计扣 " + Math.min(deducted, 100) + " 分");
    }

    // ================================================================
    // 辅助
    // ================================================================

    private LocalDateTime windowStart(int days) {
        return LocalDateTime.now().minusDays(days);
    }

    private ReputationVO.DimensionVO dimension(String key, String label, Integer score,
                                               int samples, String summary) {
        return new ReputationVO.DimensionVO(key, label, score, samples, summary);
    }

    /** 间隔秒数的人类可读描述（分钟/小时） */
    private static String humanMinutes(double minutes) {
        if (minutes < 60) {
            return Math.round(minutes) + " 分钟";
        }
        return String.format("%.1f", minutes / 60.0) + " 小时";
    }

    private static int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private static double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
    }

    private record ReviewAggregate(long total, long accurateTotal, double avgRating) {
    }
}
