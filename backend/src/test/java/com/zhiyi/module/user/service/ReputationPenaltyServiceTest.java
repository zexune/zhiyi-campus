package com.zhiyi.module.user.service;

import com.zhiyi.module.user.entity.ReputationPenalty;
import com.zhiyi.module.user.mapper.ReputationPenaltyMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationPenaltyServiceTest {

    @Mock private ReputationPenaltyMapper penaltyMapper;

    private ReputationPenaltyService service;

    @BeforeEach
    void setUp() {
        service = new ReputationPenaltyService(penaltyMapper);
    }

    @Test
    void shouldRecordPermanentBanAsFortyPointPenalty() {
        when(penaltyMapper.selectOne(any())).thenReturn(null);

        ReputationPenalty result = service.recordPenalty(
                8L, 2L, 1L, "BAN_PERM", "严重违规");

        assertEquals(8L, result.getReportId());
        assertEquals(2L, result.getUserId());
        assertEquals(1L, result.getAdminId());
        assertEquals("BAN_PERM", result.getType());
        assertEquals(40, result.getPoints());
        assertEquals("ACTIVE", result.getStatus());
        verify(penaltyMapper).insert(result);
    }

    @Test
    void shouldApplyConfiguredPointsForWarningAndTemporaryBan() {
        when(penaltyMapper.selectOne(any())).thenReturn(null);

        ReputationPenalty warning = service.recordPenalty(
                9L, 2L, 1L, " warning ", "首次违规");
        ReputationPenalty temporaryBan = service.recordPenalty(
                10L, 2L, 1L, "ban_temp", "再次违规");

        assertEquals("WARNING", warning.getType());
        assertEquals(5, warning.getPoints());
        assertEquals("BAN_TEMP", temporaryBan.getType());
        assertEquals(15, temporaryBan.getPoints());
        verify(penaltyMapper).insert(warning);
        verify(penaltyMapper).insert(temporaryBan);
    }

    @Test
    void shouldReuseExistingPenaltyForSameReport() {
        ReputationPenalty existing = penalty(15);
        when(penaltyMapper.selectOne(any())).thenReturn(existing);

        ReputationPenalty result = service.recordPenalty(
                8L, 2L, 1L, "BAN_TEMP", "重复请求");

        assertSame(existing, result);
        verify(penaltyMapper, never()).insert(any());
    }

    @Test
    void shouldCalculateComplianceFromActivePenaltyPointsAndClampAtZero() {
        when(penaltyMapper.selectList(any())).thenReturn(List.of(
                penalty(5), penalty(15), penalty(40), penalty(50)));

        assertEquals(110, service.activePenaltyPoints(2L));
        assertEquals(0, service.complianceScore(2L));
    }

    private ReputationPenalty penalty(int points) {
        ReputationPenalty penalty = new ReputationPenalty();
        penalty.setPoints(points);
        penalty.setStatus("ACTIVE");
        return penalty;
    }
}
