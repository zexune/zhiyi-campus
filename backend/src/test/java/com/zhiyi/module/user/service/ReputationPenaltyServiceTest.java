package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.module.user.entity.ReputationPenalty;
import com.zhiyi.module.user.mapper.ReputationPenaltyMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReputationPenaltyServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace("com.zhiyi.module.user.mapper.ReputationPenaltyMapper");
        TableInfoHelper.initTableInfo(assistant, ReputationPenalty.class);
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
        assertEquals("CONTENT_WARNING", result.getType());
        assertEquals(5, result.getPoints());
        assertEquals("ACTIVE", result.getStatus());
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

    private ReputationPenalty penalty(int points) {
        ReputationPenalty penalty = new ReputationPenalty();
        penalty.setPoints(points);
        penalty.setStatus("ACTIVE");
        return penalty;
    }
}
