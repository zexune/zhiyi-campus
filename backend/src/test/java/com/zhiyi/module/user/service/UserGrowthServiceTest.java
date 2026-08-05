package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.module.user.entity.ExpLog;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.event.UserLevelUpEvent;
import com.zhiyi.module.user.mapper.ExpLogMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserGrowthServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ExpLogMapper expLogMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private UserGrowthService service;

    @BeforeEach
    void setUp() {
        service = new UserGrowthService(userMapper, expLogMapper, eventPublisher);
    }

    @Test
    void deductionClampsExpButKeepsRequestedDeltaAndLevel() {
        when(userMapper.incrExp(1L, -30)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 0, 2));

        service.addExp(1L, -30, "商品被管理员强制下架");

        ArgumentCaptor<ExpLog> captor = ArgumentCaptor.forClass(ExpLog.class);
        verify(expLogMapper).insert(captor.capture());
        assertEquals(-30, captor.getValue().getDelta());
        assertEquals(0, captor.getValue().getExpAfter());
        assertEquals(2, captor.getValue().getLevelAfter());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deductionBelowThresholdNeverDowngrades() {
        when(userMapper.incrExp(1L, -30)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 70, 2));

        service.addExp(1L, -30, "违规扣分");

        ArgumentCaptor<ExpLog> captor = ArgumentCaptor.forClass(ExpLog.class);
        verify(expLogMapper).insert(captor.capture());
        assertEquals(2, captor.getValue().getLevelAfter());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void crossingThresholdUpgradesAndPublishesEvent() {
        when(userMapper.incrExp(1L, 50)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 300, 2));

        service.addExp(1L, 50, "完成订单");

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(patch.capture());
        assertEquals(3, patch.getValue().getLevel());

        ArgumentCaptor<UserLevelUpEvent> event =
                ArgumentCaptor.forClass(UserLevelUpEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(new UserLevelUpEvent(1L, 2, 3, 300), event.getValue());
    }

    @Test
    void missingUserDoesNotWriteLogOrPublishEvent() {
        when(userMapper.incrExp(404L, 50)).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.addExp(404L, 50, "完成订单"));

        verifyNoInteractions(expLogMapper, eventPublisher);
        verify(userMapper, never()).selectGrowthState(any());
        verify(userMapper, never()).updateById(any(SysUser.class));
    }

    @Test
    void oneChangeCanCrossMultipleThresholds() {
        when(userMapper.incrExp(1L, 1_000)).thenReturn(1);
        when(userMapper.selectGrowthState(1L)).thenReturn(state(1L, 1_000, 1));

        service.addExp(1L, 1_000, "历史数据补偿");

        ArgumentCaptor<UserLevelUpEvent> event =
                ArgumentCaptor.forClass(UserLevelUpEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(new UserLevelUpEvent(1L, 1, 5, 1_000), event.getValue());
    }

    private SysUser state(Long id, int exp, int level) {
        SysUser state = new SysUser();
        state.setId(id);
        state.setExp(exp);
        state.setLevel(level);
        return state;
    }
}
