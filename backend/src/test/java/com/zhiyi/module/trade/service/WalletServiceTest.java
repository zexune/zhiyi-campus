package com.zhiyi.module.trade.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.common.enums.WalletLogType;
import com.zhiyi.common.support.IdempotencyService;
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
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.*;

/**
 * WalletService 单元测试 —— 适配 v3.1 并发重构：
 * recharge(userId, amount, idempotencyKey) 经幂等协议执行（mock IdempotencyService
 * 直接运行业务函数）；doRecharge 内 selectByIdForUpdate → ACTIVE 校验 → 原子加钱 → 流水。
 */
@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock private SysUserMapper sysUserMapper;
    @Mock private WalletLogMapper walletLogMapper;
    @Mock private IdempotencyService idempotencyService;

    private WalletService walletService;

    private static final String KEY = "22222222-2222-2222-2222-222222222222";

    @BeforeEach
    void setUp() {
        walletService = new WalletService(sysUserMapper, walletLogMapper, idempotencyService);
        // 跳过幂等协议：直接执行业务 Supplier（第 6 个参数）
        lenient().doAnswer(invocation -> ((Supplier<?>) invocation.getArgument(5)).get())
                .when(idempotencyService)
                .execute(any(), any(), any(), any(), any(), any());
    }

    private SysUser activeUser(BigDecimal balance) {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(UserStatus.ACTIVE);
        user.setWalletBalance(balance);
        return user;
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
            when(sysUserMapper.selectByIdForUpdate(1L)).thenReturn(activeUser(new BigDecimal("150.00")));
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(1L)).thenReturn(activeUser(new BigDecimal("250.00")));
            BigDecimal amount = new BigDecimal("100.00");

            WalletBalanceVO vo = walletService.recharge(1L, amount, KEY);

            assertEquals(new BigDecimal("250.00"), vo.getBalance());

            // 验证流水
            ArgumentCaptor<WalletLog> captor = ArgumentCaptor.forClass(WalletLog.class);
            verify(walletLogMapper).insert(captor.capture());
            WalletLog log = captor.getValue();
            assertEquals(1L, log.getUserId());
            assertEquals(WalletLogType.RECHARGE, log.getType());
            assertEquals(amount, log.getAmount());
            assertEquals(new BigDecimal("250.00"), log.getBalanceAfter());
        }

        @Test
        void shouldRejectNullUserId() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> walletService.recharge(null, new BigDecimal("100.00"), KEY));
            assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        }

        @Test
        void shouldRejectNullAmount() {
            assertThrows(BusinessException.class,
                    () -> walletService.recharge(1L, null, KEY));
        }

        @Test
        void shouldRejectNonPositiveAmount() {
            assertThrows(BusinessException.class,
                    () -> walletService.recharge(1L, BigDecimal.ZERO, KEY));
            assertThrows(BusinessException.class,
                    () -> walletService.recharge(1L, new BigDecimal("-1.00"), KEY));
        }

        @Test
        void shouldRejectOversizedAmount() {
            BusinessException ex = assertThrows(BusinessException.class,
                    () -> walletService.recharge(1L, new BigDecimal("100000.01"), KEY));
            assertEquals(ResultCode.BAD_REQUEST.getCode(), ex.getCode());
        }

        @Test
        void shouldDelegateToIdempotencyProtocolWithNormalizedAmount() {
            when(sysUserMapper.selectByIdForUpdate(1L)).thenReturn(activeUser(new BigDecimal("150.00")));
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(1L)).thenReturn(activeUser(new BigDecimal("250.00")));

            walletService.recharge(1L, new BigDecimal("100.00"), KEY);

            // 充值经幂等协议执行：操作名 RECHARGE，金额统一 setScale(2) 后参与请求哈希
            verify(idempotencyService).execute(eq(1L), eq(IdempotencyService.OP_RECHARGE),
                    eq(KEY), eq(java.util.Map.of("amount", new BigDecimal("100.00"))),
                    eq(WalletBalanceVO.class), any());
        }

        @Test
        void shouldThrowWhenUserNotFound() {
            when(sysUserMapper.selectByIdForUpdate(1L)).thenReturn(null);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> walletService.recharge(1L, new BigDecimal("100.00"), KEY));
            assertEquals(ResultCode.USER_NOT_FOUND.getCode(), ex.getCode());
            verify(walletLogMapper, never()).insert(any(WalletLog.class));
        }

        @Test
        void shouldThrowWhenUserNotActive() {
            SysUser banned = activeUser(new BigDecimal("150.00"));
            banned.setStatus(UserStatus.BANNED_TEMP);
            when(sysUserMapper.selectByIdForUpdate(1L)).thenReturn(banned);

            BusinessException ex = assertThrows(BusinessException.class,
                    () -> walletService.recharge(1L, new BigDecimal("100.00"), KEY));
            assertEquals(ResultCode.USER_STATUS_ERROR.getCode(), ex.getCode());
            verify(sysUserMapper, never()).update(nullable(SysUser.class), any());
            verify(walletLogMapper, never()).insert(any(WalletLog.class));
        }

        @Test
        void shouldRechargeMinimumAmount() {
            when(sysUserMapper.selectByIdForUpdate(1L)).thenReturn(activeUser(BigDecimal.ZERO));
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(1L)).thenReturn(activeUser(new BigDecimal("0.01")));
            BigDecimal minAmount = new BigDecimal("0.01");

            WalletBalanceVO vo = walletService.recharge(1L, minAmount, KEY);

            assertEquals(new BigDecimal("0.01"), vo.getBalance());
        }

        @Test
        void shouldRechargeLargeAmount() {
            when(sysUserMapper.selectByIdForUpdate(1L)).thenReturn(activeUser(BigDecimal.ZERO));
            when(sysUserMapper.update(nullable(SysUser.class), any())).thenReturn(1);
            when(sysUserMapper.selectById(1L)).thenReturn(activeUser(new BigDecimal("10000.00")));
            BigDecimal largeAmount = new BigDecimal("10000.00");

            WalletBalanceVO vo = walletService.recharge(1L, largeAmount, KEY);

            assertEquals(new BigDecimal("10000.00"), vo.getBalance());
        }
    }
}
