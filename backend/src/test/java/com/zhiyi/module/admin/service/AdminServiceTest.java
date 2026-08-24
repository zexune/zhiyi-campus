package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.admin.dto.ConfirmViolationDTO;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.ViolationVO;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.OrderCancelReason;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.ViolationSource;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.CategoryMapper;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.service.LocalContentAnalyzer;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.service.ForceCancelService;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：AdminManageService 去掉 UserStateCache（主库直读鉴权）；
 * AdminViolationService 依赖 ForceCancelService + ModerationProjectionService，
 * 确认违规先强制撤单再抢占审核记录并重新投影商品状态。
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(ViolationReport.class, ViolationReportMapper.class);
    }

    @Nested
    class ResetPassword {
        @Mock private SysUserMapper userMapper;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private CategoryMapper categoryMapper;
        @Mock private LocalContentAnalyzer contentAnalyzer;
        private AdminManageService service;

        @BeforeEach
        void setUp() {
            service = new AdminManageService(userMapper, passwordEncoder,
                    categoryMapper, contentAnalyzer);
        }

        @Test
        void rejectsAdminPasswordReset() {
            SysUser admin = new SysUser();
            admin.setId(1L);
            admin.setRole(UserRole.ADMIN);
            when(userMapper.selectById(1L)).thenReturn(admin);

            assertThrows(BusinessException.class, () -> service.resetPassword(1L, 99L));
        }

        @Test
        void rejectsSystemAccountPasswordReset() {
            SysUser system = new SysUser();
            system.setId(1L);
            system.setRole(UserRole.ADMIN);
            system.setIsSystem(true);
            when(userMapper.selectById(1L)).thenReturn(system);

            assertThrows(BusinessException.class, () -> service.resetPassword(1L, 99L));
        }

        @Test
        void resetsRegularUserAndInvalidatesToken() {
            SysUser user = new SysUser();
            user.setId(2L);
            user.setRole(UserRole.USER);
            when(userMapper.selectById(2L)).thenReturn(user);
            when(passwordEncoder.encode("123456")).thenReturn("hash");
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
            when(userMapper.bumpTokenVersion(2L)).thenReturn(1);

            assertDoesNotThrow(() -> service.resetPassword(2L, 99L));

            verify(userMapper).bumpTokenVersion(2L);
        }
    }

    @Nested
    class ViolationReview {
        @Mock private ViolationReportMapper reportMapper;
        @Mock private SysUserMapper userMapper;
        @Mock private ItemMapper itemMapper;
        @Mock private TradeOrderMapper orderMapper;
        @Mock private ViolationLogMapper violationLogMapper;
        @Mock private ReputationPenaltyService penaltyService;
        @Mock private ForceCancelService forceCancelService;
        @Mock private ModerationProjectionService projectionService;
        private AdminViolationService service;

        @BeforeEach
        void setUp() {
            service = new AdminViolationService(reportMapper, userMapper, itemMapper,
                    orderMapper, violationLogMapper, penaltyService,
                    forceCancelService, projectionService);
        }

        @Test
        void returnsEmptyListWhenNoReviewsExist() {
            Page<ViolationReport> page = new Page<>(1, 10, 0);
            page.setRecords(List.of());
            when(reportMapper.selectPage(any(), any())).thenReturn(page);

            assertTrue(service.getViolations(1, 10, "PENDING").getRecords().isEmpty());
        }

        @Test
        void mapsSellerReporterSourceAndRuleEvidence() {
            ViolationReport report = pendingReport("USER_REPORT");
            report.setReporterId(11L);
            report.setMatchedRules(List.of());
            report.setRuleVersion("2026.1");
            Page<ViolationReport> page = new Page<>(1, 10, 1);
            page.setRecords(List.of(report));
            SysUser seller = user(10L, "卖家张三");
            SysUser reporter = user(11L, "举报人李四");
            Item item = new Item();
            item.setId(100L);
            item.setStatus(ItemStatus.ON_SALE);
            when(reportMapper.selectPage(any(), any())).thenReturn(page);
            when(userMapper.selectByIds(anyCollection())).thenReturn(List.of(seller, reporter));
            when(itemMapper.selectByIds(anyCollection())).thenReturn(List.of(item));

            ViolationVO vo = service.getViolations(1, 10, "PENDING").getRecords().get(0);

            assertEquals("卖家张三", vo.getSellerName());
            assertEquals("举报人李四", vo.getReporterName());
            assertEquals("USER_REPORT", vo.getSource());
            assertEquals("2026.1", vo.getRuleVersion());
            assertEquals("ON_SALE", vo.getItemStatus());
        }

        @Test
        void dismissesLocalRuleReviewAndProjectsModerationState() {
            ViolationReport report = pendingReport("LOCAL_RULE");
            when(reportMapper.selectById(1L)).thenReturn(report);
            when(reportMapper.update(isNull(), any())).thenReturn(1);

            service.dismissViolation(1L, 99L);

            // 商品审核状态由投影服务重新计算，审核工作台不再直接改商品行
            verify(itemMapper).selectByIdForUpdate(100L);
            verify(projectionService).projectItemModerationStatus(100L);
            verifyNoInteractions(penaltyService, forceCancelService);
        }

        @Test
        void confirmingContentViolationForceCancelsActiveOrderFirst() {
            ViolationReport report = pendingReport("LOCAL_RULE");
            TradeOrder active = new TradeOrder();
            active.setId(5L);
            active.setItemId(100L);
            active.setBuyerId(1L);
            active.setSellerId(10L);
            when(reportMapper.selectById(1L)).thenReturn(report);
            when(orderMapper.selectActiveByItemId(100L)).thenReturn(active);
            when(reportMapper.update(isNull(), any())).thenReturn(1);

            service.confirmViolation(1L, confirmDto(), 99L);

            // 有挂单时：先锁买家用户行与商品行，再强制撤单（ADMIN_FORCE）
            verify(userMapper).selectByIdForUpdate(1L);
            verify(itemMapper).selectByIdForUpdate(100L);
            verify(forceCancelService).cancelActiveOrderOfItem(eq(100L),
                    eq(OrderCancelReason.ADMIN_FORCE), any(), any());
            verify(projectionService).projectItemModerationStatus(100L);
            verify(penaltyService).recordContentWarning(1L, 10L, 99L, "人工确认存在违规内容");
        }

        @Test
        void confirmingContentViolationWithoutActiveOrderSkipsForceCancel() {
            ViolationReport report = pendingReport("LOCAL_RULE");
            when(reportMapper.selectById(1L)).thenReturn(report);
            when(orderMapper.selectActiveByItemId(100L)).thenReturn(null);
            when(reportMapper.update(isNull(), any())).thenReturn(1);
            // 无锁预读无挂单：子例程仍被调用（持锁重查防 RR 快照漏单），内部返回 false 无单可撤
            when(forceCancelService.cancelActiveOrderOfItem(eq(100L),
                    eq(OrderCancelReason.ADMIN_FORCE), any(), any())).thenReturn(false);

            service.confirmViolation(1L, confirmDto(), 99L);

            verify(itemMapper).selectByIdForUpdate(100L);
            verify(forceCancelService).cancelActiveOrderOfItem(eq(100L),
                    eq(OrderCancelReason.ADMIN_FORCE), any(), any());
            verify(projectionService).projectItemModerationStatus(100L);
            verify(penaltyService).recordContentWarning(1L, 10L, 99L, "人工确认存在违规内容");
        }

        @Test
        void confirmFailsWhenReportAlreadyHandledByAnotherAdmin() {
            ViolationReport report = pendingReport("LOCAL_RULE");
            when(reportMapper.selectById(1L)).thenReturn(report);
            when(orderMapper.selectActiveByItemId(100L)).thenReturn(null);
            when(reportMapper.update(isNull(), any())).thenReturn(0); // 抢占失败

            assertThrows(BusinessException.class,
                    () -> service.confirmViolation(1L, confirmDto(), 99L));
            verifyNoInteractions(penaltyService);
        }

        private ConfirmViolationDTO confirmDto() {
            ConfirmViolationDTO dto = new ConfirmViolationDTO();
            dto.setReason("人工确认存在违规内容");
            dto.setHandleNote("复核完成");
            return dto;
        }

        private ViolationReport pendingReport(String source) {
            ViolationReport report = new ViolationReport();
            report.setId(1L);
            report.setUserId(10L);
            report.setItemId(100L);
            report.setOriginalTitle("待审核商品");
            report.setOriginalDescription("待审核描述");
            report.setViolationReason("检测或举报依据");
            report.setViolationType("KEYWORD_MATCH");
            report.setSource(ViolationSource.valueOf(source));
            report.setStatus(ViolationStatus.PENDING);
            return report;
        }

        private SysUser user(Long id, String nickname) {
            SysUser user = new SysUser();
            user.setId(id);
            user.setNickname(nickname);
            return user;
        }
    }
}
