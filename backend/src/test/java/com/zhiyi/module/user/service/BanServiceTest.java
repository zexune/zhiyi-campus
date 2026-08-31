package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.enums.OrderCancelReason;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.trade.service.ForceCancelService;
import com.zhiyi.module.user.dto.AdminUserSearchQuery;
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.vo.UserVO;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：punish 改为"锁定用户行 + 条件状态迁移（setSql 推进 token_version）
 * + ForceCancelService 自动撤单 + Outbox 通知"；UserPunishedEvent/UserStateCache 已删除。
 */
@ExtendWith(MockitoExtension.class)
class BanServiceTest {

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(SysUser.class, SysUserMapper.class);
        initialize(School.class, SchoolMapper.class);
    }

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SchoolMapper schoolMapper;
    @Mock
    private ViolationLogMapper violationLogMapper;
    @Mock
    private ForceCancelService forceCancelService;
    @Mock
    private OutboxService outboxService;

    @Captor
    private ArgumentCaptor<Page<SysUser>> pageCaptor;
    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<SysUser>> wrapperCaptor;

    private BanService service;

    @BeforeEach
    void setUp() {
        service = new BanService(
                userMapper, schoolMapper, violationLogMapper, forceCancelService, outboxService);
    }

    @Test
    void temporaryBanMigratesStateForceCancelsBuyerOrdersAndNotifies() {
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(activeUser(2L));
        when(userMapper.update(any(), any())).thenReturn(1);

        service.punish(punishment("BAN_TEMP", 7), 99L);

        // 条件状态迁移（同 SQL 推进 token_version，不再单独调 bumpTokenVersion）
        verify(userMapper).update(any(), any());
        verify(userMapper, never()).bumpTokenVersion(any());
        // 同一事务内自动取消其作为买家的进行中订单
        verify(forceCancelService).cancelActiveOrdersOfBuyer(eq(2L),
                eq(OrderCancelReason.AUTO_CANCEL), any(), any());
        // 处罚日志 + 封禁通知（Outbox，与业务同事务）
        verify(violationLogMapper).insert(any(ViolationLog.class));
        verify(outboxService).appendNotice(eq("USER:2:BANNED:null"),
                eq(OutboxService.AGGREGATE_USER), eq(2L),
                eq(OutboxService.EVENT_USER_PUNISHED), eq(2L), contains("BAN_TEMP"));
    }

    @Test
    void permanentBanMigratesStateAndNotifies() {
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(activeUser(2L));
        when(userMapper.update(any(), any())).thenReturn(1);

        service.punish(punishment("BAN_PERM", null), 99L);

        verify(userMapper).update(any(), any());
        verify(forceCancelService).cancelActiveOrdersOfBuyer(eq(2L),
                eq(OrderCancelReason.AUTO_CANCEL), any(), any());
        verify(outboxService).appendNotice(any(), eq(OutboxService.AGGREGATE_USER), eq(2L),
                eq(OutboxService.EVENT_USER_PUNISHED), eq(2L), contains("BAN_PERM"));
    }

    @Test
    void invalidPunishmentDoesNotPersistOrNotify() {
        assertThrows(BusinessException.class,
                () -> service.punish(punishment("UNKNOWN", null), 99L));

        verify(userMapper, never()).update(any(), any());
        verify(violationLogMapper, never()).insert(any(ViolationLog.class));
        verifyNoNotice();
    }

    @Test
    void invalidTemporaryBanDaysHaveNoSideEffects() {
        assertThrows(BusinessException.class,
                () -> service.punish(punishment("BAN_TEMP", 0), 99L));

        verify(userMapper, never()).selectByIdForUpdate(any());
        verify(userMapper, never()).update(any(), any());
        verify(violationLogMapper, never()).insert(any(ViolationLog.class));
        verifyNoNotice();
    }

    @Test
    void administratorCannotBePunished() {
        SysUser admin = activeUser(2L);
        admin.setRole(UserRole.ADMIN);
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(admin);

        assertThrows(BusinessException.class,
                () -> service.punish(punishment("BAN_PERM", null), 99L));

        verify(userMapper, never()).update(any(), any());
        verify(violationLogMapper, never()).insert(any(ViolationLog.class));
        verifyNoNotice();
    }

    @Test
    void stateTransitionFailureBlocksEverything() {
        // 已注销或已被并发处罚：条件 UPDATE 影响 0 行，整个事务拒绝
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(activeUser(2L));
        when(userMapper.update(any(), any())).thenReturn(0);

        assertThrows(BusinessException.class,
                () -> service.punish(punishment("BAN_PERM", null), 99L));

        verify(forceCancelService, never()).cancelActiveOrdersOfBuyer(any(), any(), any(), any());
        verify(violationLogMapper, never()).insert(any(ViolationLog.class));
        verifyNoNotice();
    }

    @Test
    void unbanMigratesOnlyBannedStatesAndNotifies() {
        SysUser user = activeUser(2L);
        user.setStatus(UserStatus.BANNED_PERM);
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user);
        when(userMapper.update(any(), any())).thenReturn(1);
        ViolationLog banLog = new ViolationLog();
        banLog.setId(77L);
        when(violationLogMapper.selectLatestBanLog(2L)).thenReturn(banLog);

        service.unban(2L, 99L);

        verify(userMapper).update(any(), any());
        verify(userMapper, never()).bumpTokenVersion(any());
        // 确定性 event_id：以被解除的封禁日志主键为键，事件类型为独立的 USER_UNBANNED
        verify(outboxService).appendNotice(eq("USER:2:UNBANNED:77"),
                eq(OutboxService.AGGREGATE_USER), eq(2L),
                eq(OutboxService.EVENT_USER_UNBANNED), eq(2L), contains("解封"));
    }

    @Test
    void unbanSkipsNoticeWhenBanLogMissing() {
        // 数据不一致（封禁态但无封禁日志）：放弃通知并告警，不做静默兜底
        SysUser user = activeUser(2L);
        user.setStatus(UserStatus.BANNED_PERM);
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(user);
        when(userMapper.update(any(), any())).thenReturn(1);
        when(violationLogMapper.selectLatestBanLog(2L)).thenReturn(null);

        service.unban(2L, 99L);

        verify(userMapper).update(any(), any());
        verify(outboxService, never()).appendNotice(any(), any(), any(), any(), any(), any());
    }

    @Test
    void unbanRejectsActiveUser() {
        when(userMapper.selectByIdForUpdate(2L)).thenReturn(activeUser(2L));

        assertThrows(BusinessException.class, () -> service.unban(2L, 99L));

        verify(userMapper, never()).update(any(), any());
        verifyNoNotice();
    }

    @Test
    void searchUsersFillsSchoolNameWithOneBatchQuery() {
        SysUser first = listableUser(10L, 1L);
        SysUser second = listableUser(11L, 2L);
        when(userMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SysUser> page = invocation.getArgument(0);
            page.setRecords(List.of(first, second));
            page.setTotal(2);
            return page;
        });
        School shu = new School();
        shu.setId(1L);
        shu.setName("上海大学");
        School dhu = new School();
        dhu.setId(2L);
        dhu.setName("东华大学");
        when(schoolMapper.selectList(any())).thenReturn(List.of(shu, dhu));

        IPage<UserVO> result = service.searchUsers(new AdminUserSearchQuery(null, "2024", null, null, null), 1, 10);

        assertEquals(2, result.getTotal());
        assertEquals("上海大学", result.getRecords().get(0).getSchoolName());
        assertEquals("东华大学", result.getRecords().get(1).getSchoolName());
        verify(schoolMapper).selectList(any());
    }

    @Test
    void searchUsersWithoutSchoolRowsSkipsSchoolLookup() {
        when(userMapper.selectPage(any(), any())).thenAnswer(this::emptyPage);

        IPage<UserVO> result = service.searchUsers(AdminUserSearchQuery.EMPTY, 1, 10);

        assertEquals(0, result.getRecords().size());
        verify(schoolMapper, never()).selectList(any());
    }

    @Test
    void searchUsersCapsPageSizeAt50() {
        when(userMapper.selectPage(any(), any())).thenAnswer(this::emptyPage);

        service.searchUsers(AdminUserSearchQuery.EMPTY, 1, 500);

        verify(userMapper).selectPage(pageCaptor.capture(), any());
        assertEquals(50, pageCaptor.getValue().getSize());
    }

    @Test
    void searchUsersAlwaysScopesToRegularUsers() {
        when(userMapper.selectPage(any(), any())).thenAnswer(this::emptyPage);

        service.searchUsers(AdminUserSearchQuery.EMPTY, 1, 10);

        // 查询固定限定 role=USER 且排除 SYSTEM：管理员/技术主体不进入可检索名单
        verify(userMapper).selectPage(any(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("role"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(UserRole.USER));
    }

    private void verifyNoNotice() {
        verify(outboxService, never()).appendNotice(any(), any(), any(), any(), any(), any());
    }

    private Page<SysUser> emptyPage(InvocationOnMock invocation) {
        Page<SysUser> page = invocation.getArgument(0);
        page.setRecords(List.of());
        page.setTotal(0);
        return page;
    }

    private BanUserDTO punishment(String type, Integer banDays) {
        BanUserDTO dto = new BanUserDTO();
        dto.setUserId(2L);
        dto.setType(type);
        dto.setReason("测试原因");
        dto.setBanDays(banDays);
        return dto;
    }

    private SysUser listableUser(Long id, Long schoolId) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setStudentId("2024" + id);
        user.setNickname("检索用户" + id);
        user.setSchoolId(schoolId);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setLevel(1);
        user.setExp(0);
        user.setWalletBalance(BigDecimal.ZERO);
        return user;
    }

    private SysUser activeUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setIsSystem(false);
        return user;
    }
}
