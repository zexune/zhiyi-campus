package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.user.dto.CancelAccountDTO;
import com.zhiyi.module.user.dto.ChangePasswordDTO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LoginAttemptService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static com.zhiyi.testsupport.MybatisMetadata.initialize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 适配 v3.1 并发重构：注销先 selectByIdForUpdate 锁定本人行再检查，
 * 状态迁移为条件 UPDATE（同 SQL 推进 token_version）；UserStateCache/TagQueryService 已移除。
 */
@ExtendWith(MockitoExtension.class)
class AccountSecurityServiceTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private ItemMapper itemMapper;
    @Mock
    private TradeOrderMapper orderMapper;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private LoginAttemptService loginAttemptService;

    private AccountSecurityService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(SysUser.class, SysUserMapper.class);
        initialize(Item.class, ItemMapper.class);
        initialize(TradeOrder.class, TradeOrderMapper.class);
    }

    @BeforeEach
    void setUp() {
        service = new AccountSecurityService(
                userMapper,
                itemMapper,
                orderMapper,
                passwordEncoder,
                loginAttemptService);
    }

    @Test
    void changePasswordUpdatesHashAndBumpsTokenVersion() {
        when(userMapper.selectById(1L)).thenReturn(normalUser());
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("newpass", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("newpass")).thenReturn("new-hash");
        when(userMapper.bumpTokenVersion(1L)).thenReturn(1);

        service.changePassword(1L, changePasswordDto());

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(patch.capture());
        assertEquals("new-hash", patch.getValue().getPassword());
        verify(userMapper).bumpTokenVersion(1L);
        verify(loginAttemptService).reset("chpw:1");
    }

    @Test
    void wrongOldPasswordRecordsFailure() {
        when(userMapper.selectById(1L)).thenReturn(normalUser());
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(false);

        assertThrows(BusinessException.class,
                () -> service.changePassword(1L, changePasswordDto()));

        verify(loginAttemptService).recordFailure("chpw:1");
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(userMapper, never()).bumpTokenVersion(any());
    }

    @Test
    void cancelledAccountMigratesStateAndOffShelvesItems() {
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(normalUser());
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(true);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.update(any(), any())).thenReturn(1);

        service.cancelAccount(1L, cancelDto());

        // 在售商品条件 UPDATE（ON_SALE → OFF_SHELF）
        verify(itemMapper).update(any(), any());
        // 用户条件状态迁移（ACTIVE → CANCELLED，同 SQL 推进 token_version）
        verify(userMapper).update(any(), any());
        verify(userMapper, never()).bumpTokenVersion(any());
    }

    @Test
    void cancelWithActiveOrderChangesNothing() {
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(normalUser());
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(true);
        when(orderMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.cancelAccount(1L, cancelDto()));

        assertEquals(ResultCode.CONFLICT.getCode(), exception.getCode());
        verify(itemMapper, never()).update(any(), any());
        verify(userMapper, never()).update(any(), any());
        verify(userMapper, never()).bumpTokenVersion(any());
    }

    @Test
    void concurrentStateChangeBlocksCancellation() {
        // 锁后重读发现已被封禁：仅 ACTIVE 可注销，条件迁移失败
        SysUser banned = normalUser();
        banned.setStatus(UserStatus.BANNED_TEMP);
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(banned);
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(true);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.cancelAccount(1L, cancelDto()));

        assertEquals(ResultCode.CONFLICT.getCode(), exception.getCode());
        verify(orderMapper, never()).selectCount(any());
        verify(userMapper, never()).update(any(), any());
    }

    @Test
    void adminAccountCannotBeCancelled() {
        SysUser admin = normalUser();
        admin.setRole(UserRole.ADMIN);
        when(userMapper.selectByIdForUpdate(1L)).thenReturn(admin);

        assertThrows(BusinessException.class, () -> service.cancelAccount(1L, cancelDto()));

        verify(orderMapper, never()).selectCount(any());
    }

    private ChangePasswordDTO changePasswordDto() {
        ChangePasswordDTO dto = new ChangePasswordDTO();
        dto.setOldPassword("oldpass");
        dto.setNewPassword("newpass");
        dto.setConfirmPassword("newpass");
        return dto;
    }

    private CancelAccountDTO cancelDto() {
        CancelAccountDTO dto = new CancelAccountDTO();
        dto.setPassword("oldpass");
        return dto;
    }

    private SysUser normalUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStudentId("user01");
        user.setPassword("old-hash");
        user.setRole(UserRole.USER);
        user.setStatus(UserStatus.ACTIVE);
        user.setIsSystem(false);
        return user;
    }
}
