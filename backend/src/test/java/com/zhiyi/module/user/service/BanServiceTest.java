package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.user.dto.AdminUserSearchQuery;
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.entity.School;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.event.UserPunishedEvent;
import com.zhiyi.module.user.mapper.SchoolMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.RecordingUserStateCache;
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
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private RecordingUserStateCache userStateCache;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    // 泛型 Captor 用注解创建，避免 forClass(Class) 的非受检调用
    @Captor
    private ArgumentCaptor<Page<SysUser>> pageCaptor;
    @Captor
    private ArgumentCaptor<LambdaQueryWrapper<SysUser>> wrapperCaptor;

    private BanService service;

    @BeforeEach
    void setUp() {
        userStateCache = new RecordingUserStateCache(userMapper);
        service = new BanService(
                userMapper, schoolMapper, violationLogMapper, userStateCache, eventPublisher);
    }

    @Test
    void temporaryBanEventContainsDeadline() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L));
        when(userMapper.bumpTokenVersion(2L)).thenReturn(1);

        service.punish(punishment("BAN_TEMP", 7), 99L);

        ArgumentCaptor<UserPunishedEvent> event =
                ArgumentCaptor.forClass(UserPunishedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(7, event.getValue().banDays());
        assertNotNull(event.getValue().banUntilTime());
        verify(userMapper).bumpTokenVersion(2L);
    }

    @Test
    void permanentBanBumpsTokenVersion() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L));
        when(userMapper.bumpTokenVersion(2L)).thenReturn(1);

        service.punish(punishment("BAN_PERM", null), 99L);

        verify(userMapper).bumpTokenVersion(2L);
    }

    @Test
    void invalidPunishmentDoesNotPersistOrPublish() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L));

        assertThrows(BusinessException.class,
                () -> service.punish(punishment("UNKNOWN", null), 99L));

        verify(violationLogMapper, never()).insert(any(ViolationLog.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void invalidTemporaryBanDaysHaveNoSideEffects() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L));

        assertThrows(BusinessException.class,
                () -> service.punish(punishment("BAN_TEMP", 0), 99L));

        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(userMapper, never()).bumpTokenVersion(any());
        verify(violationLogMapper, never()).insert(any(ViolationLog.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        assertTrue(userStateCache.afterCommitInvalidations().isEmpty());
    }

    @Test
    void administratorCannotBePunished() {
        SysUser admin = activeUser(2L);
        admin.setRole(UserRole.ADMIN);
        when(userMapper.selectById(2L)).thenReturn(admin);

        assertThrows(BusinessException.class,
                () -> service.punish(punishment("BAN_PERM", null), 99L));

        verify(violationLogMapper, never()).insert(any(ViolationLog.class));
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unbanInvalidatesStateAfterCommit() {
        SysUser user = activeUser(2L);
        user.setStatus(UserStatus.BANNED_PERM);
        when(userMapper.selectById(2L)).thenReturn(user);

        service.unban(2L, 99L);

        assertEquals(List.of(2L), userStateCache.afterCommitInvalidations());
        assertTrue(userStateCache.immediateInvalidations().isEmpty());
        verify(userMapper, never()).bumpTokenVersion(any());
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
        when(userMapper.selectPage(any(), any())).thenAnswer(invocation -> {
            Page<SysUser> page = invocation.getArgument(0);
            page.setRecords(List.of());
            page.setTotal(0);
            return page;
        });

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

        // 查询固定限定 role=USER：管理员账号不进入可检索/可处罚名单
        verify(userMapper).selectPage(any(), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSegment().contains("role"));
        assertTrue(wrapperCaptor.getValue().getParamNameValuePairs().containsValue(UserRole.USER));
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
        return user;
    }
}
