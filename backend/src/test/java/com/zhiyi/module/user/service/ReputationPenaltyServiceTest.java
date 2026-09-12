package com.zhiyi.module.user.service;

import com.zhiyi.module.user.entity.ReputationPenalty;
import com.zhiyi.common.enums.PenaltyStatus;
import com.zhiyi.common.enums.PenaltyType;
import com.zhiyi.module.user.mapper.ReputationPenaltyMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationPenaltyServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(ReputationPenalty.class, ReputationPenaltyMapper.class);
    }

    @Mock private ReputationPenaltyMapper penaltyMapper;
    private ReputationPenaltyService service;

    @BeforeEach
    void setUp() {
        service = new ReputationPenaltyService(penaltyMapper);
    }

    @Test
    void recordsFixedFivePointContentWarning() {
        when(penaltyMapper.selectOne(any())).thenReturn(null);

        ReputationPenalty result = service.recordContentWarning(8L, 2L, 1L, "确认内容违规");

        assertEquals(8L, result.getReportId());
        assertEquals(PenaltyType.CONTENT_WARNING, result.getType());
        assertEquals(5, result.getPoints());
        assertEquals(PenaltyStatus.ACTIVE, result.getStatus());
        verify(penaltyMapper).insert(result);
    }

    @Test
    void reusesExistingPenaltyForSameReport() {
        ReputationPenalty existing = penalty(5);
        when(penaltyMapper.selectOne(any())).thenReturn(existing);

        ReputationPenalty result = service.recordContentWarning(8L, 2L, 1L, "重复请求");

        assertSame(existing, result);
        verify(penaltyMapper, never()).insert(any(ReputationPenalty.class));
    }

    @Test
    void revocationIsIdempotent() {
        when(penaltyMapper.update(isNull(), any())).thenReturn(1, 0);

        assertTrue(service.revokePenalty(8L));
        assertFalse(service.revokePenalty(8L));
    }

    @Test
    void calculatesComplianceFromActivePenaltyPointsAndClampsAtZero() {
        when(penaltyMapper.selectList(any())).thenReturn(List.of(
                penalty(5), penalty(15), penalty(40), penalty(50)));

        assertEquals(110, service.activePenaltyPoints(2L));
        assertEquals(0, service.complianceScore(2L));
    }

    @Test
    void penaltyPointsDecayLinearlyWithAge() {
        // 10 分处罚，90 天（半个衰减窗口）→ 有效 5 分
        ReputationPenalty halfAged = penaltyAt(10, LocalDateTime.now().minusDays(90));
        when(penaltyMapper.selectList(any())).thenReturn(List.of(halfAged));

        assertEquals(5, service.activePenaltyPoints(2L));
        assertEquals(95, service.complianceScore(2L));
    }

    @Test
    void penaltiesBeyondDecayWindowNaturallyExpire() {
        ReputationPenalty expired = penaltyAt(10, LocalDateTime.now().minusDays(200));
        when(penaltyMapper.selectList(any())).thenReturn(List.of(expired));

        assertEquals(0, service.activePenaltyPoints(2L));
        assertEquals(100, service.complianceScore(2L));
        assertTrue(service.activePenaltiesWithDecay(2L).isEmpty());
    }

    @Test
    void freshPenaltyDeductsFullPoints() {
        when(penaltyMapper.selectList(any())).thenReturn(
                List.of(penaltyAt(10, LocalDateTime.now())));

        assertEquals(10, service.activePenaltyPoints(2L));
    }

    private ReputationPenalty penalty(int points) {
        ReputationPenalty penalty = new ReputationPenalty();
        penalty.setId((long) points);
        penalty.setPoints(points);
        penalty.setStatus(PenaltyStatus.ACTIVE);
        return penalty;
    }

    private ReputationPenalty penaltyAt(int points, LocalDateTime createdAt) {
        ReputationPenalty penalty = penalty(points);
        penalty.setCreatedAt(createdAt);
        return penalty;
    }
}
