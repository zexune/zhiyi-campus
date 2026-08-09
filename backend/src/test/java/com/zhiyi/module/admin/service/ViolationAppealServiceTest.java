package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.admin.dto.HandleAppealDTO;
import com.zhiyi.module.admin.dto.SubmitAppealDTO;
import com.zhiyi.module.admin.entity.ViolationAppeal;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationAppealMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.service.TagQueryService;
import com.zhiyi.common.enums.AppealStatus;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViolationAppealServiceTest {

    @Mock private ViolationAppealMapper appealMapper;
    @Mock private ViolationReportMapper reportMapper;
    @Mock private ItemMapper itemMapper;
    @Mock private SysUserMapper userMapper;
    @Mock private ReputationPenaltyService penaltyService;
    @Mock private TagQueryService tagQueryService;
    private ViolationAppealService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(ViolationAppeal.class, "com.zhiyi.module.admin.mapper.ViolationAppealMapper");
        initialize(ViolationReport.class, "com.zhiyi.module.admin.mapper.ViolationReportMapper");
    }

    private static void initialize(Class<?> entity, String namespace) {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        service = new ViolationAppealService(appealMapper, reportMapper, itemMapper,
                userMapper, penaltyService, tagQueryService);
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
    void approvalRevokesExactPenaltyAndRelistsWhenNoOtherConfirmedCaseExists() {
        ViolationAppeal appeal = new ViolationAppeal();
        appeal.setId(20L);
        appeal.setReportId(8L);
        appeal.setItemId(100L);
        appeal.setUserId(2L);
        appeal.setStatus(AppealStatus.PENDING);
        ViolationReport report = confirmedReport(LocalDateTime.now().minusDays(1));
        Item item = new Item();
        item.setId(100L);
        item.setStatus(ItemStatus.OFF_SHELF);
        item.setModerationStatus(ModerationStatus.REJECTED);
        when(appealMapper.selectById(20L)).thenReturn(appeal);
        when(appealMapper.update(isNull(), any())).thenReturn(1);
        when(reportMapper.selectById(8L)).thenReturn(report);
        when(reportMapper.update(isNull(), any())).thenReturn(1);
        when(reportMapper.selectCount(any())).thenReturn(0L);
        when(itemMapper.selectById(100L)).thenReturn(item);

        service.approve(20L, 99L, new HandleAppealDTO("复核后确认原判有误"));

        verify(penaltyService).revokePenalty(8L);
        assertEquals(ModerationStatus.PASSED, item.getModerationStatus());
        assertEquals(ItemStatus.ON_SALE, item.getStatus());
        verify(itemMapper).updateById(item);
    }

    @Test
    void approvalDoesNotRelistWhenANewerConfirmedCaseExists() {
        ViolationAppeal appeal = new ViolationAppeal();
        appeal.setId(20L);
        appeal.setReportId(8L);
        appeal.setItemId(100L);
        appeal.setUserId(2L);
        appeal.setStatus(AppealStatus.PENDING);
        Item item = new Item();
        item.setId(100L);
        item.setStatus(ItemStatus.OFF_SHELF);
        item.setModerationStatus(ModerationStatus.REJECTED);
        when(appealMapper.selectById(20L)).thenReturn(appeal);
        when(appealMapper.update(isNull(), any())).thenReturn(1);
        when(reportMapper.selectById(8L)).thenReturn(confirmedReport(LocalDateTime.now().minusDays(1)));
        when(reportMapper.update(isNull(), any())).thenReturn(1);
        when(reportMapper.selectCount(any())).thenReturn(1L);
        when(itemMapper.selectById(100L)).thenReturn(item);

        service.approve(20L, 99L, new HandleAppealDTO("The old decision is incorrect"));

        verify(penaltyService).revokePenalty(8L);
        assertEquals(ModerationStatus.REJECTED, item.getModerationStatus());
        assertEquals(ItemStatus.OFF_SHELF, item.getStatus());
        verify(itemMapper, never()).updateById(any(Item.class));
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
