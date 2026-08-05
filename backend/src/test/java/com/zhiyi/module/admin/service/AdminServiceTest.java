package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.admin.dto.ConfirmViolationDTO;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.ViolationVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.BanService;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import com.zhiyi.module.user.service.UserGrowthService;
import com.zhiyi.module.user.support.UserStateCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Admin 服务层单元测试 —— 覆盖 AdminManageService（强制下架/重置密码）
 * 和 AdminViolationService（违规审核）。
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    /* ---- AdminManageService ---- */

    @Nested
    class ForceOffShelf {

        @Mock private ItemMapper itemMapper;
        @Mock private SysUserMapper sysUserMapper;
        @Mock private ViolationLogMapper violationLogMapper;
        @Mock private UserGrowthService growthService;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private UserStateCache userStateCache;

        private AdminManageService service;

        @BeforeEach
        void setUp() {
            service = new AdminManageService(itemMapper, sysUserMapper,
                    violationLogMapper, growthService, passwordEncoder, userStateCache);
        }

        private Item onSaleItem() {
            Item item = new Item();
            item.setId(1L);
            item.setStatus("ON_SALE");
            item.setTitle("测试商品");
            item.setPublisherId(2L);
            return item;
        }

        @Test
        void shouldForceOffShelfSuccessfully() {
            Item item = onSaleItem();
            when(itemMapper.selectById(1L)).thenReturn(item);

            service.forceOffShelf(1L, 99L);

            assertEquals("OFF_SHELF", item.getStatus());
            verify(itemMapper).updateById(item);
            verify(growthService).addExp(eq(2L), eq(UserGrowthService.EXP_FORCED_OFF_SHELF), anyString());
            verify(violationLogMapper).insert(any(ViolationLog.class));
        }

        @Test
        void shouldRejectNonExistentItem() {
            when(itemMapper.selectById(999L)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> service.forceOffShelf(999L, 99L));
        }

        @Test
        void shouldRejectAlreadyOffShelf() {
            Item item = onSaleItem();
            item.setStatus("OFF_SHELF");
            when(itemMapper.selectById(1L)).thenReturn(item);

            assertThrows(BusinessException.class,
                    () -> service.forceOffShelf(1L, 99L));
        }

        @Test
        void shouldRecordViolationLogWithCorrectInfo() {
            Item item = onSaleItem();
            when(itemMapper.selectById(1L)).thenReturn(item);

            service.forceOffShelf(1L, 99L);

            verify(violationLogMapper).insert(argThat(log ->
                    log.getUserId().equals(2L)
                    && log.getAdminId().equals(99L)
                    && "WARNING".equals(log.getType())
                    && log.getReason().contains("强制下架")
            ));
        }
    }

    @Nested
    class ResetPassword {

        @Mock private ItemMapper itemMapper;
        @Mock private SysUserMapper sysUserMapper;
        @Mock private ViolationLogMapper violationLogMapper;
        @Mock private UserGrowthService growthService;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private UserStateCache userStateCache;

        private AdminManageService service;

        @BeforeEach
        void setUp() {
            service = new AdminManageService(itemMapper, sysUserMapper,
                    violationLogMapper, growthService, passwordEncoder, userStateCache);
        }

        @Test
        void shouldRejectAdminPasswordReset() {
            SysUser admin = new SysUser();
            admin.setId(1L);
            admin.setRole("ADMIN");
            when(sysUserMapper.selectById(1L)).thenReturn(admin);

            assertThrows(BusinessException.class,
                    () -> service.resetPassword(1L, 99L));
        }

        @Test
        void shouldResetPasswordSuccessfully() {
            SysUser user = new SysUser();
            user.setId(2L);
            user.setRole("USER");
            user.setNickname("测试用户");
            when(sysUserMapper.selectById(2L)).thenReturn(user);
            when(passwordEncoder.encode("123456")).thenReturn("$2a$10$hashed");
            when(sysUserMapper.updateById(any())).thenReturn(1);
            when(sysUserMapper.bumpTokenVersion(2L)).thenReturn(1);

            // userStateCache.invalidateAfterCommit is void
            doNothing().when(userStateCache).invalidateAfterCommit(2L);

            assertDoesNotThrow(() -> service.resetPassword(2L, 99L));
        }
    }

    /* ---- AdminViolationService ---- */

    @Nested
    class ViolationReview {

        @Mock private ViolationReportMapper violationReportMapper;
        @Mock private SysUserMapper sysUserMapper;
        @Mock private ItemMapper itemMapper;
        @Mock private BanService banService;
        @Mock private ViolationLogMapper violationLogMapper;
        @Mock private ReputationPenaltyService reputationPenaltyService;

        private AdminViolationService service;

        @BeforeEach
        void setUp() {
            service = new AdminViolationService(
                    violationReportMapper, sysUserMapper, itemMapper, banService, violationLogMapper,
                    reputationPenaltyService);
        }

        @Test
        void shouldReturnEmptyListWhenNoViolations() {
            Page<ViolationReport> emptyPage = new Page<>(1, 10);
            emptyPage.setRecords(List.of());
            emptyPage.setTotal(0);
            when(violationReportMapper.selectPage(any(Page.class), any())).thenReturn(emptyPage);

            IPage<ViolationVO> result = service.getViolations(1, 10, "PENDING");

            assertTrue(result.getRecords().isEmpty());
        }

        @Test
        void shouldPopulateViolationVOFields() {
            ViolationReport report = new ViolationReport();
            report.setId(1L);
            report.setUserId(10L);
            report.setOriginalTitle("违规商品标题");
            report.setOriginalDescription("违规描述");
            report.setViolationType("CONTENT_VIOLATION");
            report.setViolationReason("AI检测到违禁内容");
            report.setStatus("PENDING");
            report.setItemId(100L);

            Page<ViolationReport> page = new Page<>(1, 10);
            page.setRecords(List.of(report));
            page.setTotal(1);

            SysUser reporter = new SysUser();
            reporter.setId(10L);
            reporter.setNickname("发布者张三");

            Item item = new Item();
            item.setId(100L);
            item.setStatus("OFF_SHELF");

            when(violationReportMapper.selectPage(any(Page.class), any())).thenReturn(page);
            when(sysUserMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(reporter));
            when(itemMapper.selectBatchIds(List.of(100L))).thenReturn(List.of(item));

            IPage<ViolationVO> result = service.getViolations(1, 10, "PENDING");

            assertEquals(1, result.getRecords().size());
            ViolationVO vo = result.getRecords().get(0);
            assertEquals("违规商品标题", vo.getOriginalTitle());
            assertEquals("发布者张三", vo.getReporterName());
            assertEquals("OFF_SHELF", vo.getItemStatus());
            assertEquals(100L, vo.getItemId());
        }

        @Test
        void shouldDismissPendingViolationWithoutChangingTradeReviews() {
            ViolationReport report = new ViolationReport();
            report.setId(1L);
            report.setUserId(10L);
            report.setStatus("PENDING");
            report.setItemId(100L);

            Item item = new Item();
            item.setId(100L);
            item.setStatus("OFF_SHELF");
            item.setAiReviewed(false);

            when(violationReportMapper.selectById(1L)).thenReturn(report);
            when(itemMapper.selectById(100L)).thenReturn(item);

            service.dismissViolation(1L, 99L);

            assertEquals("DISMISSED", report.getStatus());
            assertEquals(99L, report.getHandlerId());
            assertNotNull(report.getHandledAt());
            assertEquals("ON_SALE", item.getStatus());
            assertEquals(Boolean.TRUE, item.getAiReviewed());
            verify(violationReportMapper).updateById(report);
            verify(itemMapper).updateById(item);
            verifyNoInteractions(reputationPenaltyService);
        }

        @Test
        void shouldConfirmViolationWithIndependentReputationPenalty() {
            ViolationReport report = new ViolationReport();
            report.setId(1L);
            report.setUserId(10L);
            report.setStatus("PENDING");
            when(violationReportMapper.selectById(1L)).thenReturn(report);

            ConfirmViolationDTO dto = new ConfirmViolationDTO();
            dto.setType("BAN_TEMP");
            dto.setReason("重复发布违规内容");
            dto.setBanDays(7);
            dto.setHandleNote("人工复核确认");

            service.confirmViolation(1L, dto, 99L);

            assertEquals("CONFIRMED", report.getStatus());
            assertEquals(99L, report.getHandlerId());
            verify(banService).punish(argThat(punishment ->
                    punishment.getUserId().equals(10L)
                            && "BAN_TEMP".equals(punishment.getType())
                            && punishment.getBanDays().equals(7)), eq(99L));
            verify(reputationPenaltyService).recordPenalty(
                    1L, 10L, 99L, "BAN_TEMP", "重复发布违规内容");
        }
    }
}
