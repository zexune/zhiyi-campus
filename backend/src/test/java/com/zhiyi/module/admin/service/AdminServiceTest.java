package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.module.admin.dto.ConfirmViolationDTO;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.ViolationVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.service.TagQueryService;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.ViolationSource;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.trade.mapper.ItemReservationMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import com.zhiyi.module.user.support.UserStateCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        MapperBuilderAssistant reportAssistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        reportAssistant.setCurrentNamespace("com.zhiyi.module.admin.mapper.ViolationReportMapper");
        TableInfoHelper.initTableInfo(reportAssistant, ViolationReport.class);
    }

    @Nested
    class ForceOffShelf {
        @Mock private ItemMapper itemMapper;
        @Mock private SysUserMapper userMapper;
        @Mock private ItemReservationMapper reservationMapper;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private UserStateCache userStateCache;
        @Mock private TagQueryService tagQueryService;
        private AdminManageService service;

        @BeforeEach
        void setUp() {
            service = new AdminManageService(itemMapper, userMapper, reservationMapper,
                    passwordEncoder, userStateCache, tagQueryService);
        }

        @Test
        void forceOffShelfOnlyChangesItemState() {
            Item item = onSaleItem();
            when(itemMapper.selectById(1L)).thenReturn(item);

            service.forceOffShelf(1L, 99L);

            assertEquals(ItemStatus.OFF_SHELF, item.getStatus());
            verify(itemMapper).updateById(item);
            verifyNoInteractions(userMapper, userStateCache);
        }

        @Test
        void rejectsReservedItem() {
            Item item = onSaleItem();
            when(itemMapper.selectById(1L)).thenReturn(item);
            when(reservationMapper.selectById(1L)).thenReturn(new com.zhiyi.module.trade.entity.ItemReservation());

            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.forceOffShelf(1L, 99L));

            assertEquals(409, error.getCode());
            verify(itemMapper, never()).updateById(any(Item.class));
        }

        @Test
        void rejectsAlreadyOffShelf() {
            Item item = onSaleItem();
            item.setStatus(ItemStatus.OFF_SHELF);
            when(itemMapper.selectById(1L)).thenReturn(item);

            assertThrows(BusinessException.class, () -> service.forceOffShelf(1L, 99L));
        }

        private Item onSaleItem() {
            Item item = new Item();
            item.setId(1L);
            item.setStatus(ItemStatus.ON_SALE);
            item.setTitle("测试商品");
            item.setPublisherId(2L);
            return item;
        }
    }

    @Nested
    class ResetPassword {
        @Mock private ItemMapper itemMapper;
        @Mock private SysUserMapper userMapper;
        @Mock private ItemReservationMapper reservationMapper;
        @Mock private PasswordEncoder passwordEncoder;
        @Mock private UserStateCache userStateCache;
        @Mock private TagQueryService tagQueryService;
        private AdminManageService service;

        @BeforeEach
        void setUp() {
            service = new AdminManageService(itemMapper, userMapper, reservationMapper,
                    passwordEncoder, userStateCache, tagQueryService);
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
        void resetsRegularUserAndInvalidatesToken() {
            SysUser user = new SysUser();
            user.setId(2L);
            user.setRole(UserRole.USER);
            when(userMapper.selectById(2L)).thenReturn(user);
            when(passwordEncoder.encode("123456")).thenReturn("hash");
            when(userMapper.updateById(any(SysUser.class))).thenReturn(1);
            when(userMapper.bumpTokenVersion(2L)).thenReturn(1);

            assertDoesNotThrow(() -> service.resetPassword(2L, 99L));

            verify(userStateCache).invalidateAfterCommit(2L);
        }
    }

    @Nested
    class ViolationReview {
        @Mock private ViolationReportMapper reportMapper;
        @Mock private SysUserMapper userMapper;
        @Mock private ItemMapper itemMapper;
        @Mock private ViolationLogMapper violationLogMapper;
        @Mock private ReputationPenaltyService penaltyService;
        @Mock private TagQueryService tagQueryService;
        private AdminViolationService service;

        @BeforeEach
        void setUp() {
            service = new AdminViolationService(reportMapper, userMapper, itemMapper,
                    violationLogMapper, penaltyService, tagQueryService);
        }

        @Test
        void returnsEmptyListWhenNoReviewsExist() {
            Page<ViolationReport> page = new Page<>(1, 10, 0);
            page.setRecords(List.of());
            when(reportMapper.selectPage(any(Page.class), any())).thenReturn(page);

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
            when(reportMapper.selectPage(any(Page.class), any())).thenReturn(page);
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
        void dismissesLocalRuleReviewAndRelistsItem() {
            ViolationReport report = pendingReport("LOCAL_RULE");
            Item item = new Item();
            item.setId(100L);
            item.setStatus(ItemStatus.ON_SALE);
            item.setModerationStatus(ModerationStatus.PENDING);
            when(reportMapper.selectById(1L)).thenReturn(report);
            when(reportMapper.update(isNull(), any())).thenReturn(1);
            when(itemMapper.selectById(100L)).thenReturn(item);

            service.dismissViolation(1L, 99L);

            assertEquals(ModerationStatus.PASSED, item.getModerationStatus());
            assertEquals(ItemStatus.ON_SALE, item.getStatus());
            verify(itemMapper).updateById(item);
            verifyNoInteractions(penaltyService);
        }

        @Test
        void confirmingContentViolationDownshelvesAndRecordsFixedWarning() {
            ViolationReport report = pendingReport("LOCAL_RULE");
            Item item = new Item();
            item.setId(100L);
            item.setStatus(ItemStatus.ON_SALE);
            item.setModerationStatus(ModerationStatus.PENDING);
            when(reportMapper.selectById(1L)).thenReturn(report);
            when(reportMapper.update(isNull(), any())).thenReturn(1);
            when(itemMapper.selectById(100L)).thenReturn(item);
            ConfirmViolationDTO dto = new ConfirmViolationDTO();
            dto.setReason("人工确认存在违规内容");
            dto.setHandleNote("复核完成");

            service.confirmViolation(1L, dto, 99L);

            assertEquals(ModerationStatus.REJECTED, item.getModerationStatus());
            assertEquals(ItemStatus.OFF_SHELF, item.getStatus());
            verify(penaltyService).recordContentWarning(1L, 10L, 99L, "人工确认存在违规内容");
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
