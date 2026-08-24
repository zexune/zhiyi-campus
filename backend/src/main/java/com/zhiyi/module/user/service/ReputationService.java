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
 * 信誉雷达聚合服务（A6）—— 把交易、评价、聊天和独立处罚记录折算成六维 0-100 分值。
 *
 * 固定成本读取（B10 根因修复）：
 * - 响应速度不再扫描全部聊天记录，只读 user_reputation_metric 固定大小指标行
 *   （由消息写入链路的唯一贡献样本增量维护）；无样本返回中性基线（统计中语义）；
 * - 评价维度使用单条聚合 SQL（COUNT/AVG/SUM），返回行数恒为 1，
 *   不把全量评价装载进内存。
 * 派生统计不得成为交易、鉴权或处罚的权威来源；历史数据回填按主键游标离线分批执行，
 * 未回填完成期间返回基线值，禁止回退到在线全量扫描。
 */
@Service
@RequiredArgsConstructor
public class ReputationService {

    /** 无样本时的中性基线 */
    private static final int NEUTRAL_BASELINE = 60;
    /** 活跃度统计窗口 */
    private static final int ACTIVITY_WINDOW_DAYS = 30;

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
        int completionRate = completionRate(userId);
        int responseSpeed = responseSpeed(userId);

        ReviewAggregate reviews = reviewAggregate(userId);
        int accuracy = accuracy(reviews);
        int praise = praise(reviews);
        int activity = activity(userId);
        int compliance = penaltyService.complianceScore(userId);

        return new ReputationVO(userId, completionRate, responseSpeed,
                accuracy, praise, activity, compliance, (int) reviews.total());
    }

    /** 交易完成率：completed / (completed + cancelled)，无单给基线。 */
    private int completionRate(Long userId) {
        long completed = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.COMPLETED));
        long cancelled = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.CANCELLED));
        long total = completed + cancelled;
        if (total == 0) {
            return NEUTRAL_BASELINE;
        }
        return clamp((int) Math.round(completed * 100.0 / total));
    }

    /**
     * 响应速度：读取固定大小派生指标（平均首响间隔），不扫描聊天明细。
     * 无样本返回基线（统计中）。
     */
    private int responseSpeed(Long userId) {
        UserReputationMetric metric = metricMapper.selectById(userId);
        if (metric == null || metric.getSampleCount() == null || metric.getSampleCount() <= 0) {
            return NEUTRAL_BASELINE;
        }
        long totalGapSeconds = metric.getTotalGapSeconds() == null ? 0 : metric.getTotalGapSeconds();
        double avgMinutes = (totalGapSeconds / (double) metric.getSampleCount()) / 60.0;
        // 5 分钟内视为满分，之后线性衰减，12 小时(720min)以上降到 20 分。
        if (avgMinutes <= 5) {
            return 100;
        }
        int score = (int) Math.round(100 - (avgMinutes - 5) / 715.0 * 80);
        return clamp(score);
    }

    /** 评价聚合（单行聚合 SQL）：总数、平均星级、描述准确数。 */
    private ReviewAggregate reviewAggregate(Long userId) {
        List<Map<String, Object>> rows = reviewMapper.selectMaps(new QueryWrapper<TradeReview>()
                .select("COUNT(*) AS total",
                        "COALESCE(AVG(rating), 0) AS avg_rating",
                        "COALESCE(SUM(accurate), 0) AS accurate_total")
                .eq("target_id", userId));
        if (rows.isEmpty() || rows.getFirst() == null) {
            return new ReviewAggregate(0, 0, 0);
        }
        Map<String, Object> row = rows.getFirst();
        return new ReviewAggregate(
                toLong(row.get("total")),
                toLong(row.get("accurate_total")),
                toDouble(row.get("avg_rating")));
    }

    /** 描述准确度：accurate 评价占比，无评价给基线。 */
    private int accuracy(ReviewAggregate reviews) {
        if (reviews.total() == 0) {
            return NEUTRAL_BASELINE;
        }
        return clamp((int) Math.round(reviews.accurateTotal() * 100.0 / reviews.total()));
    }

    /** 历史好评：平均星级折算百分制，无评价给基线。 */
    private int praise(ReviewAggregate reviews) {
        if (reviews.total() == 0) {
            return NEUTRAL_BASELINE;
        }
        return clamp((int) Math.round(reviews.avgRating() / 5.0 * 100));
    }

    /** 活跃度：近 30 天发布数 + 成交数，封顶 100。 */
    private int activity(Long userId) {
        LocalDateTime since = LocalDateTime.now().minusDays(ACTIVITY_WINDOW_DAYS);
        long published = itemMapper.selectCount(new LambdaQueryWrapper<Item>()
                .eq(Item::getPublisherId, userId)
                .ge(Item::getCreatedAt, since));
        long traded = orderMapper.selectCount(new LambdaQueryWrapper<TradeOrder>()
                .eq(TradeOrder::getSellerId, userId)
                .eq(TradeOrder::getStatus, OrderStatus.COMPLETED)
                .ge(TradeOrder::getCompletedAt, since));
        return clamp((int) Math.min(100, published + traded));
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private long toLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) return number.doubleValue();
        return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
    }

    private record ReviewAggregate(long total, long accurateTotal, double avgRating) {
    }
}
