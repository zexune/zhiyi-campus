package com.zhiyi.module.trade.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.mapper.WalletLogMapper;
import com.zhiyi.module.trade.vo.WalletBalanceVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * WalletService 单元测试 —— 覆盖余额查询、充值的正常路径与边界条件。
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private WalletLogMapper walletLogMapper;

    private WalletService walletService;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(sysUserMapper, walletLogMapper);
    }

    // ================================================================
    // 余额查询
    // ================================================================

    @Nested
    class GetBalance {

        @Test
        void shouldReturnBalance() {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setWalletBalance(new BigDecimal("150.00"));
            when(sysUserMapper.selectById(1L)).thenReturn(user);

            WalletBalanceVO vo = walletService.getBalance(1L);

            assertEquals(new BigDecimal("150.00"), vo.getBalance());
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(sysUserMapper.selectById(999L)).thenReturn(null);

            assertThrows(BusinessException.class,
                    () -> walletService.getBalance(999L));
        }

        @Test
        void shouldHandleZeroBalance() {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setWalletBalance(BigDecimal.ZERO);
            when(sysUserMapper.selectById(1L)).thenReturn(user);

            WalletBalanceVO vo = walletService.getBalance(1L);

            assertEquals(BigDecimal.ZERO, vo.getBalance());
        }
    }

    // ================================================================
    // 充值
    // ================================================================

    @Nested
    class Recharge {

        @Test
        void shouldRechargeSuccessfully() {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setWalletBalance(new BigDecimal("250.00"));
            BigDecimal amount = new BigDecimal("100.00");

            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(1L)).thenReturn(user);

            WalletBalanceVO vo = walletService.recharge(1L, amount);

            assertEquals(new BigDecimal("250.00"), vo.getBalance());

            // 验证流水
            ArgumentCaptor<WalletLog> captor = ArgumentCaptor.forClass(WalletLog.class);
            verify(walletLogMapper).insert(captor.capture());
            WalletLog log = captor.getValue();
            assertEquals(1L, log.getUserId());
            assertEquals("RECHARGE", log.getType());
            assertEquals(amount, log.getAmount());
            assertEquals(new BigDecimal("250.00"), log.getBalanceAfter());
        }

        @Test
        void shouldRejectNullUserId() {
            assertThrows(BusinessException.class,
                    () -> walletService.recharge(null, new BigDecimal("100.00")));
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(BusinessException.class,
                    () -> walletService.recharge(1L, null));
        }

        @Test
        void shouldThrowWhenUpdateFails() {
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(0);

            assertThrows(BusinessException.class,
                    () -> walletService.recharge(1L, new BigDecimal("100.00")));
        }

        @Test
        void shouldRechargeMinimumAmount() {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setWalletBalance(new BigDecimal("0.01"));
            BigDecimal minAmount = new BigDecimal("0.01");

            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(1L)).thenReturn(user);

            WalletBalanceVO vo = walletService.recharge(1L, minAmount);

            assertEquals(new BigDecimal("0.01"), vo.getBalance());
        }

        @Test
        void shouldRechargeLargeAmount() {
            SysUser user = new SysUser();
            user.setId(1L);
            user.setWalletBalance(new BigDecimal("10000.00"));
            BigDecimal largeAmount = new BigDecimal("10000.00");

            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(1L)).thenReturn(user);

            WalletBalanceVO vo = walletService.recharge(1L, largeAmount);

            assertEquals(new BigDecimal("10000.00"), vo.getBalance());
        }
    }
}
