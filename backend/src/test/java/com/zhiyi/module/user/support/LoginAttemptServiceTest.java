package com.zhiyi.module.user.support;

import com.zhiyi.module.user.mapper.LoginAttemptMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：失败限流从本地 Caffeine 改为数据库固定窗口状态机，
 * LoginAttemptService 仅作为 REQUIRES_NEW 协调器委托 LoginAttemptMapper。
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    private static final int FAIL_LIMIT = 5;
    private static final int WINDOW_SECONDS = 900;
    private static final int LOCK_SECONDS = 300;
    private static final int RETENTION_SECONDS = 86400;

    @Mock
    private LoginAttemptMapper attemptMapper;

    private LoginAttemptService service;

    @BeforeEach
    void setUp() {
        service = new LoginAttemptService(attemptMapper, FAIL_LIMIT,
                WINDOW_SECONDS, LOCK_SECONDS, RETENTION_SECONDS);
    }

    @Test
    void isLockedDelegatesToDatabaseState() {
        when(attemptMapper.isLocked("1:admin")).thenReturn(true);

        assertTrue(service.isLocked("1:admin"));
        assertFalse(service.isLocked("2:other"));
    }

    @Test
    void recordFailurePassesPolicyParametersToStateMachine() {
        when(attemptMapper.isLocked("1:admin")).thenReturn(true);

        boolean locked = service.recordFailure("1:admin");

        assertTrue(locked);
        verify(attemptMapper).recordFailure("1:admin", WINDOW_SECONDS, FAIL_LIMIT, LOCK_SECONDS);
    }

    @Test
    void recordFailureReturnsFalseBelowThreshold() {
        when(attemptMapper.isLocked("1:admin")).thenReturn(false);

        assertFalse(service.recordFailure("1:admin"));
    }

    @Test
    void resetClearsCounterRow() {
        service.reset("1:admin");

        verify(attemptMapper).reset("1:admin");
    }

    @Test
    void purgeStaleDelegatesWithRetentionPolicy() {
        when(attemptMapper.purgeStale(RETENTION_SECONDS)).thenReturn(3);

        assertEquals(3, service.purgeStale(RETENTION_SECONDS));
    }
}
