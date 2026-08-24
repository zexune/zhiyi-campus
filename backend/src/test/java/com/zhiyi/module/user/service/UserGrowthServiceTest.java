package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.user.entity.ExpLog;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.ExpLogMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * 适配 v3.1 并发重构：升级通知从 ApplicationEventPublisher 事件
 * 改为事务 Outbox（appendNotice），业务回滚时通知随之消失。
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

        verify(outboxService).appendNotice(eq("USER:1:LEVEL_UP:5"),
                eq(OutboxService.AGGREGATE_USER), eq(1L),
                eq(OutboxService.EVENT_USER_LEVEL_UP), eq(1L), anyString());
    }

    private SysUser state(Long id, int exp, int level) {
        SysUser state = new SysUser();
        state.setId(id);
        state.setExp(exp);
        state.setLevel(level);
        return state;
    }
}
