package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.user.dto.CancelAccountDTO;
import com.zhiyi.module.user.dto.ChangePasswordDTO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LoginAttemptService;
import com.zhiyi.module.user.support.RecordingUserStateCache;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

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

    private RecordingUserStateCache userStateCache;
    private AccountSecurityService service;

    @BeforeAll
    static void initializeMyBatisMetadata() {
        initialize(SysUser.class, "com.zhiyi.module.user.mapper.SysUserMapper");
        initialize(Item.class, "com.zhiyi.module.item.mapper.ItemMapper");
        initialize(TradeOrder.class, "com.zhiyi.module.trade.mapper.TradeOrderMapper");
    }

    private static void initialize(Class<?> entity, String namespace) {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), namespace);
        assistant.setCurrentNamespace(namespace);
        TableInfoHelper.initTableInfo(assistant, entity);
    }

    @BeforeEach
    void setUp() {
        userStateCache = new RecordingUserStateCache(userMapper);
        service = new AccountSecurityService(
                userMapper,
                itemMapper,
                orderMapper,
                passwordEncoder,
                userStateCache,
                new LoginAttemptService(5, 300));
    }

    @Test
    void changePasswordInvalidatesStateAfterCommit() {
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
        assertEquals(List.of(1L), userStateCache.afterCommitInvalidations());
        assertTrue(userStateCache.immediateInvalidations().isEmpty());
    }

    @Test
    void successfulCancellationInvalidatesStateAfterCommit() {
        when(userMapper.selectById(1L)).thenReturn(normalUser());
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(true);
        when(orderMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.bumpTokenVersion(1L)).thenReturn(1);

        service.cancelAccount(1L, cancelDto());

        ArgumentCaptor<SysUser> patch = ArgumentCaptor.forClass(SysUser.class);
        verify(userMapper).updateById(patch.capture());
        assertEquals("CANCELLED", patch.getValue().getStatus());
        verify(itemMapper).update(any(Item.class), any());
        verify(userMapper).bumpTokenVersion(1L);
        assertEquals(List.of(1L), userStateCache.afterCommitInvalidations());
        assertTrue(userStateCache.immediateInvalidations().isEmpty());
    }

    @Test
    void cancelWithActiveOrderChangesNothing() {
        when(userMapper.selectById(1L)).thenReturn(normalUser());
        when(passwordEncoder.matches("oldpass", "old-hash")).thenReturn(true);
        when(orderMapper.selectCount(any())).thenReturn(1L);

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> service.cancelAccount(1L, cancelDto()));

        assertEquals(ResultCode.CONFLICT.getCode(), exception.getCode());
        verify(itemMapper, never()).update(any(Item.class), any());
        verify(userMapper, never()).updateById(any(SysUser.class));
        verify(userMapper, never()).bumpTokenVersion(any());
        assertTrue(userStateCache.afterCommitInvalidations().isEmpty());
        assertTrue(userStateCache.immediateInvalidations().isEmpty());
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
        user.setRole("USER");
        user.setStatus("ACTIVE");
        return user;
    }
}
