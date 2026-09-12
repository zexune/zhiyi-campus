package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.user.entity.ExpLog;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.ExpLogMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.ExpRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 覆盖两层：底层 addExp（原子加减、只升不降、Outbox 升级通知）与
 * 目录化 award（每日上限、一次性去重键、回购衰减系数、流水附注）。
 */
@ExtendWith(MockitoExtension.class)
class UserGrowthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ExpLogMapper expLogMapper;
    @Mock
    private OutboxService outboxService;

    private UserGrowthService service;

    @BeforeEach
    void setUp() {
        service = new UserGrowthService(userMapper, expLogMapper, outboxService);
    }

    // ================================================================
    // 底层 addExp
    // ================================================================

    @Test
    void deductionClampsExpButKeepsRequestedDeltaAndLevel() {
        when(userMapper.incrExp(1L, -30)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 0, 2));

        service.addExp(1L, -30, "管理员人工经验修正");

        ArgumentCaptor<ExpLog> captor = ArgumentCaptor.forClass(ExpLog.class);
        verify(expLogMapper).insert(captor.capture());
        assertEquals(-30, captor.getValue().getDelta());
        assertEquals(0, captor.getValue().getExpAfter());
        assertEquals(2, captor.getValue().getLevelAfter());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verifyNoInteractions(outboxService);
    }

    @Test
    void deductionBelowThresholdNeverDowngrades() {
        when(userMapper.incrExp(1L, -30)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 70, 2));

        service.addExp(1L, -30, "历史数据修正");

        ArgumentCaptor<ExpLog> captor = ArgumentCaptor.forClass(ExpLog.class);
        verify(expLogMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getLevelAfter());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verifyNoInteractions(outboxService);
    }

    @Test
    void crossingThresholdUpgradesAndAppendsOutboxNotice() {
        when(userMapper.incrExp(1L, 50)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 300, 2));

        service.addExp(1L, 50, "完成订单");

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(patch.capture());
        assertEquals(3, patch.getValue().getLevel());

        verify(outboxService).appendNotice(eq("USER:1:LEVEL_UP:3"),
                eq(OutboxService.AGGREGATE_USER), eq(1L),
                eq(OutboxService.EVENT_USER_LEVEL_UP), eq(1L), contains("Lv.3"));
    }

    @Test
    void missingUserDoesNotWriteLogOrAppendNotice() {
        when(userMapper.incrExp(404L, 50)).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.addExp(404L, 50, "完成订单"));

        verifyNoInteractions(expLogMapper, outboxService);
        verify(userMapper, never()).selectGrowthState(any());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void oneChangeCanCrossMultipleThresholds() {
        when(userMapper.incrExp(1L, 1_000)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 1_000, 1));

        service.addExp(1L, 1_000, "历史数据补偿");

        // 新 10 级曲线：1000 经验落在 Lv.5 区间 [660, 1020)，一次跨 4 级
        verify(outboxService).appendNotice(eq("USER:1:LEVEL_UP:5"),
                eq(OutboxService.AGGREGATE_USER), eq(1L),
                eq(OutboxService.EVENT_USER_LEVEL_UP), eq(1L), anyString());
    }

    // ================================================================
    // 目录化 award
    // ================================================================

    @Test
    void awardWithoutCapWritesRuleCodeAndLabel() {
        when(userMapper.incrExp(1L, ExpRule.ORDER_SELLER.points())).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 40, 1));

        service.award(1L, ExpRule.ORDER_SELLER);

        ArgumentCaptor<ExpLog> captor = ArgumentCaptor.forClass(ExpLog.class);
        verify(expLogMapper).insert(captor.capture());
        assertEquals(ExpRule.ORDER_SELLER.points(), captor.getValue().getDelta());
        assertEquals(ExpRule.ORDER_SELLER.name(), captor.getValue().getRuleCode());
        assertEquals(ExpRule.ORDER_SELLER.label(), captor.getValue().getReason());
        // 可重复来源不带去重键
        assertNull(captor.getValue().getDedupKey());
    }

    @Test
    void dailyCapReachedSkipsAwardSilently() {
        when(expLogMapper.selectCount(any())).thenReturn((long) ExpRule.ITEM_PUBLISHED.dailyCap());

        service.award(1L, ExpRule.ITEM_PUBLISHED);

        verifyNoInteractions(userMapper);
        verify(expLogMapper, never()).insert(any(ExpLog.class));
        verifyNoInteractions(outboxService);
    }

    @Test
    void oneTimeRuleAlreadyClaimedSkipsAward() {
        when(expLogMapper.selectCount(any())).thenReturn(1L);

        service.award(1L, ExpRule.PROFILE_COMPLETED);

        verifyNoInteractions(userMapper);
        verify(expLogMapper, never()).insert(any(ExpLog.class));
    }

    @Test
    void oneTimeRuleFirstClaimCarriesDedupKey() {
        when(expLogMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.incrExp(1L, ExpRule.PROFILE_COMPLETED.points())).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 20, 1));

        service.award(1L, ExpRule.PROFILE_COMPLETED);

        ArgumentCaptor<ExpLog> captor = ArgumentCaptor.forClass(ExpLog.class);
        verify(expLogMapper).insert(captor.capture());
        assertEquals(ExpRule.PROFILE_COMPLETED.dedupKey(), captor.getValue().getDedupKey());
        assertEquals(ExpRule.PROFILE_COMPLETED.name(), captor.getValue().getRuleCode());
    }

    @Test
    void pairDecayFactorScalesPointsAndAnnotatesReason() {
        // 第 3 次成交：40 × 0.5 = 20
        when(userMapper.incrExp(1L, 20)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 20, 1));

        service.award(1L, ExpRule.ORDER_SELLER, 0.5, "第 3 次成交");

        ArgumentCaptor<ExpLog> captor = ArgumentCaptor.forClass(ExpLog.class);
        verify(expLogMapper).insert(captor.capture());
        assertEquals(20, captor.getValue().getDelta());
        assertEquals("完成订单（卖出）（第 3 次成交）", captor.getValue().getReason());
    }

    @Test
    void zeroPointsAfterDecayWritesNothing() {
        service.award(1L, ExpRule.ORDER_BUYER, 0.0, null);

        verifyNoInteractions(userMapper);
        verify(expLogMapper, never()).insert(any(ExpLog.class));
    }

    private SysUser state(Long id, int exp, int level) {
        SysUser state = new SysUser();
        state.setId(id);
        state.setExp(exp);
        state.setLevel(level);
        return state;
    }
}
