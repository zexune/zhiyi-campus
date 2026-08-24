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

import java.util.List;
import java.util.Map;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：响应速度只读 UserReputationMetricMapper 指标行（不再扫描聊天），
 * 评价聚合用 reviewMapper.selectMaps 单行聚合 SQL（键 total/avg_rating/accurate_total）。
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
        lenient().when(penaltyService.complianceScore(USER_ID)).thenReturn(100);
    }

    private Map<String, Object> aggregateRow(long total, long accurateTotal, double avgRating) {
        return Map.of(
                "total", total,
                "accurate_total", accurateTotal,
                "avg_rating", avgRating);
    }

    @Test
    void brandNewUserGetsNeutralBaseline() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        // 完全没有数据：完成率/响应/好评/准确度给中性基线 60，活跃度为 0
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(0, 0, 0.0)));
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(metricMapper.selectById(USER_ID)).thenReturn(null);

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(USER_ID, vo.getUserId());
        assertEquals(60, vo.getCompletionRate());
        assertEquals(60, vo.getResponseSpeed());
        assertEquals(60, vo.getAccuracy());
        assertEquals(60, vo.getPraise());
        assertEquals(0, vo.getActivity());
        assertEquals(100, vo.getCompliance());
        assertEquals(0, vo.getReviewCount());
        assertScoresInRange(vo);
    }

    @Test
    void allDimensionsStayWithinZeroToHundred() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(50L);
        when(itemMapper.selectCount(any())).thenReturn(200L);
        when(metricMapper.selectById(USER_ID)).thenReturn(null);
        // 3 条评价，全部准确，平均 5 星（单行聚合 SQL 返回）
        when(reviewMapper.selectMaps(any())).thenReturn(
                List.of(aggregateRow(3, 3, (5.0 + 5.0 + 4.0) / 3)));

        ReputationVO vo = reputationService.compute(USER_ID);
        assertScoresInRange(vo);
        assertEquals(3, vo.getReviewCount());
        assertEquals(100, vo.getAccuracy()); // 3/3 准确
    }

    @Test
    void inaccurateReviewsLowerAccuracyScore() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(metricMapper.selectById(USER_ID)).thenReturn(null);
        // 4 条评价 1 条准确 => 25 分；平均分 (5+1+3+2)/4 = 2.75 星 => 55
        when(reviewMapper.selectMaps(any())).thenReturn(
                List.of(aggregateRow(4, 1, 2.75)));

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(25, vo.getAccuracy());
        assertEquals(55, vo.getPraise());
        assertScoresInRange(vo);
    }

    @Test
    void activePenaltiesOnlyLowerComplianceDimension() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(metricMapper.selectById(USER_ID)).thenReturn(null);
        when(reviewMapper.selectMaps(any())).thenReturn(
                List.of(aggregateRow(2, 2, 4.5)));
        when(penaltyService.complianceScore(USER_ID)).thenReturn(45);

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(100, vo.getAccuracy());
        assertEquals(90, vo.getPraise());
        assertEquals(45, vo.getCompliance());
        assertScoresInRange(vo);
    }

    @Test
    void missingUserIsRejectedBeforeAggregation() {
        when(userMapper.selectById(USER_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> reputationService.compute(USER_ID));

        assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
        verify(orderMapper, never()).selectCount(any());
    }

    @Test
    void responseSpeedReadsDerivedMetricRowOnly() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(0, 0, 0.0)));
        // 平均首响 60 分钟：100 - 55/715*80 ≈ 94
        UserReputationMetric metric = new UserReputationMetric();
        metric.setUserId(USER_ID);
        metric.setSampleCount(1);
        metric.setTotalGapSeconds(3600L);
        when(metricMapper.selectById(USER_ID)).thenReturn(metric);

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(94, vo.getResponseSpeed());
        // 响应速度不再扫描聊天明细
    }

    @Test
    void fastResponsesScoreFullMarks() {
        when(userMapper.selectById(USER_ID)).thenReturn(new SysUser());
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectCount(any())).thenReturn(0L);
        when(reviewMapper.selectMaps(any())).thenReturn(List.of(aggregateRow(0, 0, 0.0)));
        // 平均首响 2 分钟：满分
        UserReputationMetric metric = new UserReputationMetric();
        metric.setUserId(USER_ID);
        metric.setSampleCount(10);
        metric.setTotalGapSeconds(1200L);
        when(metricMapper.selectById(USER_ID)).thenReturn(metric);

        ReputationVO vo = reputationService.compute(USER_ID);

        assertEquals(100, vo.getResponseSpeed());
    }

    private void assertScoresInRange(ReputationVO vo) {
        for (int score : new int[]{vo.getCompletionRate(), vo.getResponseSpeed(),
                vo.getAccuracy(), vo.getPraise(), vo.getActivity(), vo.getCompliance()}) {
            assertTrue(score >= 0 && score <= 100,
                    "维度分值应落在 0-100，实际为 " + score);
        }
    }
}
