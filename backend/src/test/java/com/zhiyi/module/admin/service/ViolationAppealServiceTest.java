package com.zhiyi.module.admin.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.enums.AppealStatus;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.dto.HandleAppealDTO;
import com.zhiyi.module.admin.dto.SubmitAppealDTO;
import com.zhiyi.module.admin.entity.ViolationAppeal;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationAppealMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：TagQueryService 依赖已移除；approve 改为
 * "锁定商品 → 抢占申诉 → 翻案原报告 → 撤销处罚 → ModerationProjectionService 统一投影"，
 * 商品不再自动重新上架（恢复上架由卖家手动 relist）。
 */
@ExtendWith(MockitoExtension.class)
class ViolationAppealServiceTest {

    @Mock private ViolationAppealMapper appealMapper;
    @Mock private ViolationReportMapper reportMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ReputationPenaltyService penaltyService;
    @Mock private ModerationProjectionService projectionService;
    private ViolationAppealService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(ViolationAppeal.class, ViolationAppealMapper.class);
        initialize(ViolationReport.class, ViolationReportMapper.class);
    }

    @BeforeEach
    void setUp() {
        service = new ViolationAppealService(appealMapper, reportMapper, itemMapper,
                userMapper, penaltyService, projectionService);
    }

    @Test
    void acceptsOneAppealForARecentConfirmedViolation() {
        ViolationReport report = confirmedReport(LocalDateTime.now().minusDays(1));
        when(reportMapper.selectById(8L)).thenReturn(report);
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(appealMapper.insert(any(ViolationAppeal.class))).thenAnswer(invocation -> {
            ViolationAppeal appeal = invocation.getArgument(0);
            appeal.setId(20L);
            return 1;
        });

        var result = service.submit(2L, 8L, new SubmitAppealDTO("商品描述并不存在违规，请重新核实"));

        assertEquals(20L, result.id());
        ArgumentCaptor<ViolationAppeal> captor = ArgumentCaptor.forClass(ViolationAppeal.class);
        verify(appealMapper).insert(captor.capture());
        assertEquals(AppealStatus.PENDING, captor.getValue().getStatus());
        assertEquals(8L, captor.getValue().getReportId());
    }

    @Test
    void rejectsAppealOutsideConfiguredWindow() {
        when(reportMapper.selectById(8L)).thenReturn(confirmedReport(LocalDateTime.now().minusDays(8)));

        assertThrows(BusinessException.class,
                () -> service.submit(2L, 8L, new SubmitAppealDTO("已经超过申诉期限的说明文字")));

        verify(appealMapper, never()).insert(any(ViolationAppeal.class));
    }

    @Test
    void mapsConcurrentDuplicateAppealToBusinessConflict() {
        when(reportMapper.selectById(8L)).thenReturn(confirmedReport(LocalDateTime.now().minusDays(1)));
        when(appealMapper.selectCount(any())).thenReturn(0L);
        when(appealMapper.insert(any(ViolationAppeal.class)))
                .thenThrow(new DuplicateKeyException("duplicate report_id"));

        assertThrows(BusinessException.class,
                () -> service.submit(2L, 8L, new SubmitAppealDTO("Please review this decision again")));
    }

    @Test
    void approvalRevokesPenaltyAndProjectsModerationWithoutRelisting() {
        ViolationAppeal appeal = pendingAppeal();
        when(appealMapper.selectById(20L)).thenReturn(appeal);
        when(appealMapper.update(isNull(), any())).thenReturn(1);
        when(reportMapper.selectById(8L)).thenReturn(confirmedReport(LocalDateTime.now().minusDays(1)));
        when(reportMapper.update(isNull(), any())).thenReturn(1);

        service.approve(20L, 99L, new HandleAppealDTO("复核后确认原判有误"));

        // 聚合串行点：先锁商品行，再抢占更新申诉与原报告
        verify(itemMapper).selectByIdForUpdate(100L);
        verify(penaltyService).revokePenalty(8L);
        // 商品审核状态由投影服务统一计算，且不自动重新上架
        verify(projectionService).projectItemModerationStatus(100L);
        verify(itemMapper, never()).updateById(any(Item.class));
    }

    @Test
    void approvalFailsWhenAppealAlreadyHandled() {
        when(appealMapper.selectById(20L)).thenReturn(pendingAppeal());
        when(appealMapper.update(isNull(), any())).thenReturn(0); // 并发抢占失败

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.approve(20L, 99L, new HandleAppealDTO("复核后确认原判有误")));

        assertEquals(409, error.getCode());
        verify(penaltyService, never()).revokePenalty(any());
        verify(projectionService, never()).projectItemModerationStatus(any());
    }

    @Test
    void rejectMarksAppealHandledWithoutTouchingPenalty() {
        when(appealMapper.selectById(20L)).thenReturn(pendingAppeal());
        when(appealMapper.update(isNull(), any())).thenReturn(1);

        service.reject(20L, 99L, new HandleAppealDTO("维持原判"));

        verify(penaltyService, never()).revokePenalty(any());
        verify(projectionService, never()).projectItemModerationStatus(any());
    }

    private ViolationAppeal pendingAppeal() {
        ViolationAppeal appeal = new ViolationAppeal();
        appeal.setId(20L);
        appeal.setReportId(8L);
        appeal.setItemId(100L);
        appeal.setUserId(2L);
        appeal.setStatus(AppealStatus.PENDING);
        return appeal;
    }

    private ViolationReport confirmedReport(LocalDateTime handledAt) {
        ViolationReport report = new ViolationReport();
        report.setId(8L);
        report.setItemId(100L);
        report.setUserId(2L);
        report.setOriginalTitle("测试商品");
        report.setViolationReason("原违规依据");
        report.setStatus(ViolationStatus.CONFIRMED);
        report.setHandledAt(handledAt);
        return report;
    }
}
