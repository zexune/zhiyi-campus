package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.entity.TradeReview;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.mapper.TradeReviewMapper;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.entity.UserReputationMetric;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.mapper.UserReputationMetricMapper;
import com.zhiyi.module.user.vo.ReputationVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配口径统一后的雷达：比率维度 180 天窗口 + 最小样本门槛（不足不评分，
 * score=null）、响应速度读 EWMA 指标行、处罚经衰减明细聚合、活跃度加权计数。
 * 维度顺序调用约定：完成率（orderMapper.selectCount ×2）→ 响应 → 准确 →
 * 好评（reviewMapper.selectMaps ×2）→ 活跃（itemMapper + orderMapper 各 1）→ 合规。
 */
@ExtendWith(MockitoExtension.class)
class ReputationServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(TradeOrder.class, TradeOrderMapper.class);
        initialize(TradeReview.class, TradeReviewMapper.class);
        initialize(Item.class, ItemMapper.class);
    }

    @Mock private TradeOrderMapper orderMapper;
    @Mock private TradeReviewMapper reviewMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private UserReputationMetricMapper metricMapper;
    @Mock private ReputationPenaltyService penaltyService;

    private ReputationService reputationService;

    private static final Long USER_ID = 2L;

    @BeforeEach
    void setUp() {
        reputationService = new ReputationService(
                orderMapper, reviewMapper, itemMapper, userMapper, metricMapper, penaltyService);
        lenient().when(penaltyService.activePenaltiesWithDecay(USER_ID)).thenReturn(List.of());
        lenient().when(penaltyService.penaltyDecayDays()).thenReturn(180);
    }

    private Map<String, Object> aggregateRow(long total, long accurateTotal, double avgRating) {
        return Map.of(
                "total", total,
                "accurate_total", accurateTotal,
                "avg_rating", avgRating);
    }

    private ReputationVO.DimensionVO dim(ReputationVO vo, String key) {
        return vo.getDimensions().stream()
                .filter(d -> d.getKey().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少维度 " + key));
    }

    private UserReputationMetric metric(int samples, long ewmaSeconds) {
        UserReputationMetric metric = new UserReputationMetric();
        metric.setUserId(USER_ID);
        metric.setSampleCount(samples);
        metric.setEwmaGapSeconds(ewmaSeconds);
        return metric;
    }

    @Test
    void brandNewUserShowsInsufficientSamplesInsteadOfFakeBaseline() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(0, 0, 0.0)));
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(metricMapper.selectById(USER_ID)).thenReturn(null);

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(USER_ID, vo.getUserId());
        assertEquals(6, vo.getDimensions().size());
        // 无数据 ≠ 表现差：比率维度不评分（null），不再用 60 基线冒充
        assertNull(dim(vo, "completionRate").getScore());
        assertNull(dim(vo, "responseSpeed").getScore());
        assertNull(dim(vo, "accuracy").getScore());
        assertNull(dim(vo, "praise").getScore());
        // 活跃度是精确计数，0 就是 0；无处罚合规度满分
        assertEquals(0, dim(vo, "activity").getScore());
        assertEquals(100, dim(vo, "compliance").getScore());
        // 每个维度都有归因文案
        vo.getDimensions().forEach(d -> assertNotNull(d.getSummary(), d.getKey() + " 缺少归因"));
        assertScoresInRange(vo);
    }

    @Test
    void ratioDimensionsRequireMinimumSamples() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        // 完成率：4 单（<5）不评分；第 3 个返回值是活跃度的成交计数
        when(orderMapper.selectCount(any())).thenReturn(3L, 1L, 0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(metricMapper.selectById(USER_ID)).thenReturn(metric(4, 300L));
        // 4 条评价（<5）：准确度与好评都不评分
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(4, 4, 5.0)));

        ReputationVO vo = reputationService.compute(USER_ID);

        assertNull(dim(vo, "completionRate").getScore());
        assertEquals(4, dim(vo, "completionRate").getSamples());
        assertNull(dim(vo, "responseSpeed").getScore());
        assertEquals(4, dim(vo, "responseSpeed").getSamples());
        assertNull(dim(vo, "accuracy").getScore());
        assertNull(dim(vo, "praise").getScore());
        assertScoresInRange(vo);
    }

    @Test
    void sufficientSamplesScoreAllRatioDimensions() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        // 完成 11 / 取消 1 → 92 分（12 单 ≥ 门槛）
        when(orderMapper.selectCount(any())).thenReturn(11L, 1L, 2L);
        when(itemMapper.selectCount(any())).thenReturn(10L);
        // 6 条评价 5 条准确、平均 4.5 星 → 准确 83 / 好评 90
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(6, 5, 4.5)));
        when(metricMapper.selectById(USER_ID)).thenReturn(metric(6, 300L));

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(92, dim(vo, "completionRate").getScore());
        assertEquals(12, dim(vo, "completionRate").getSamples());
        assertEquals(83, dim(vo, "accuracy").getScore());
        assertEquals(90, dim(vo, "praise").getScore());
        assertEquals(6, dim(vo, "praise").getSamples());
        assertTrue(dim(vo, "praise").getSummary().contains("4.5"));
        assertScoresInRange(vo);
    }

    @Test
    void responseSpeedUsesEwmaMetricWithFloor() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(0, 0, 0.0)));
        // EWMA 首响 60 分钟：100 - 55/715×80 ≈ 94
        when(metricMapper.selectById(USER_ID)).thenReturn(metric(6, 3600L));

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(94, dim(vo, "responseSpeed").getScore());
        assertEquals(6, dim(vo, "responseSpeed").getSamples());
        // 60 分钟归一化为小时口径的人类可读描述
        assertTrue(dim(vo, "responseSpeed").getSummary().contains("小时"));
    }

    @Test
    void fastResponsesScoreFullMarks() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(0, 0, 0.0)));
        when(metricMapper.selectById(USER_ID)).thenReturn(metric(10, 120L));

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(100, dim(vo, "responseSpeed").getScore());
    }

    @Test
    void activityWeightsTradesHigherThanPublishes() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L, 0L, 3L);
        when(itemMapper.selectCount(any())).thenReturn(10L);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(0, 0, 0.0)));
        when(metricMapper.selectById(USER_ID)).thenReturn(null);

        ReputationVO vo = reputationService.compute(USER_ID);

        // 发布 ×2 + 成交 ×5 = 20 + 15 = 35
        assertEquals(35, dim(vo, "activity").getScore());
        assertEquals(13, dim(vo, "activity").getSamples());
    }

    @Test
    void decayedPenaltiesLowerComplianceDimensionOnly() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(metricMapper.selectById(USER_ID)).thenReturn(null);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(6, 6, 5.0)));
        when(penaltyService.activePenaltiesWithDecay(USER_ID)).thenReturn(List.of(
                new ReputationPenaltyService.EffectivePenalty(1L, 5, LocalDateTime.now()),
                new ReputationPenaltyService.EffectivePenalty(2L, 3, LocalDateTime.now())));

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(100, dim(vo, "accuracy").getScore());
        assertEquals(100, dim(vo, "praise").getScore());
        assertEquals(92, dim(vo, "compliance").getScore());
        assertEquals(2, dim(vo, "compliance").getSamples());
        // 归因文案引用实际配置的衰减窗口，不硬编码天数
        assertTrue(dim(vo, "compliance").getSummary().contains("180 天线性衰减"));
        assertScoresInRange(vo);
    }

    @Test
    void reviewAggregateRunsExactlyOncePerCompute() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(metricMapper.selectById(USER_ID)).thenReturn(null);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(6, 5, 4.5)));

        reputationService.compute(USER_ID);

        // 准确度与好评共用同一行聚合结果，不得重复执行同一条 COUNT/AVG/SUM
        verify(reviewMapper, times(1)).selectMaps(any());
    }

    @Test
    void missingUserIsRejectedBeforeAggregation() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reputationService.compute(USER_ID));

        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(orderMapper, never()).selectCount(any());
    }

    private void assertScoresInRange(ReputationVO vo) {
        for (ReputationVO.DimensionVO dimension : vo.getDimensions()) {
            if (dimension.getScore() == null) {
                continue;
            }
            assertTrue(dimension.getScore() >= 0 && dimension.getScore() <= 100,
                    dimension.getKey() + " 分值应落在 0-100，实际为 " + dimension.getScore());
        }
    }
}
