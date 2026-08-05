package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.event.UserPunishedEvent;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.RecordingUserStateCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BanServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ViolationLogMapper violationLogMapper;
    private RecordingUserStateCache userStateCache;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private BanService service;

    @BeforeEach
    void setUp() {
        userStateCache = new RecordingUserStateCache(userMapper);
        service = new BanService(
                userMapper, violationLogMapper, userStateCache, eventPublisher);
    }

    @Test
    void warningPersistsLogAndPublishesEvent() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L));
        BanUserDTO dto = punishment("WARNING", null);

        service.punish(dto, 99L);

        ArgumentCaptor<ViolationLog> log = ArgumentCaptor.forClass(ViolationLog.class);
        verify(violationLogMapper).insert(log.capture());
        assertEquals("WARNING", log.getValue().getType());

        ArgumentCaptor<UserPunishedEvent> event =
                ArgumentCaptor.forClass(UserPunishedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(2L, event.getValue().userId());
        assertEquals("WARNING", event.getValue().type());
        assertNull(event.getValue().banUntilTime());
        assertEquals(List.of(2L), userStateCache.afterCommitInvalidations());
        assertTrue(userStateCache.immediateInvalidations().isEmpty());
        verify(userMapper, never()).bumpTokenVersion(any());
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

        verify(violationLogMapper, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void invalidTemporaryBanDaysHaveNoSideEffects() {
        when(userMapper.selectById(2L)).thenReturn(activeUser(2L));

        assertThrows(BusinessException.class,
                () -> service.punish(punishment("BAN_TEMP", 0), 99L));

        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(userMapper, never()).bumpTokenVersion(any());
        verify(violationLogMapper, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
        assertTrue(userStateCache.afterCommitInvalidations().isEmpty());
    }

    @Test
    void administratorCannotBePunished() {
        SysUser admin = activeUser(2L);
        admin.setRole("ADMIN");
        when(userMapper.selectById(2L)).thenReturn(admin);

        assertThrows(BusinessException.class,
                () -> service.punish(punishment("WARNING", null), 99L));

        verify(violationLogMapper, never()).insert(any());
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    @Test
    void unbanInvalidatesStateAfterCommit() {
        SysUser user = activeUser(2L);
        user.setStatus("BANNED_PERM");
        when(userMapper.selectById(2L)).thenReturn(user);

        service.unban(2L, 99L);

        assertEquals(List.of(2L), userStateCache.afterCommitInvalidations());
        assertTrue(userStateCache.immediateInvalidations().isEmpty());
        verify(userMapper, never()).bumpTokenVersion(any());
    }

    private BanUserDTO punishment(String type, Integer banDays) {
        BanUserDTO dto = new BanUserDTO();
        dto.setUserId(2L);
        dto.setType(type);
        dto.setReason("测试原因");
        dto.setBanDays(banDays);
        return dto;
    }

    private SysUser activeUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setRole("USER");
        user.setStatus("ACTIVE");
        return user;
    }
}
