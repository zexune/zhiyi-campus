package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.RetryOnDeadlock;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.common.enums.WalletLogType;
import com.zhiyi.common.support.IdempotencyService;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.mapper.WalletLogMapper;
import com.zhiyi.module.trade.vo.WalletBalanceVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 钱包服务：余额查询、模拟充值、资金流水。
 *
 * 充值幂等：RECHARGE 操作由 idempotency_record 判定执行所有权；
 * 金额统一 setScale(2, HALF_UP) 后参与请求哈希，同键不同金额返回参数冲突。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WalletService {

    private final SysUserMapper sysUserMapper;
    private final WalletLogMapper walletLogMapper;
    private final IdempotencyService idempotencyService;

    /** 查询当前用户钱包余额 */
    public WalletBalanceVO getBalance(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return new WalletBalanceVO(user.getWalletBalance());
    }

    @RetryOnDeadlock
    @Transactional(rollbackFor = Exception.class)
    public WalletBalanceVO recharge(Long userId, BigDecimal amount, String idempotencyKey) {
        // Web 请求身份由 JwtInterceptor 保证：null userId 是编程错误，不是业务失败
        if (userId == null) {
            throw new IllegalStateException("userId 缺失：拦截器未注入登录身份");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "充值金额必须大于 0");
        }
        if (amount.compareTo(new BigDecimal("100000")) > 0) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "单笔充值金额过大");
        }
        BigDecimal normalized = amount.setScale(2, RoundingMode.HALF_UP);
        return idempotencyService.execute(userId, IdempotencyService.OP_RECHARGE,
                idempotencyKey, Map.of("amount", normalized), WalletBalanceVO.class,
                () -> doRecharge(userId, normalized));
    }

    private WalletBalanceVO doRecharge(Long userId, BigDecimal amount) {
        // 1. 锁定本人用户行（锁序：幂等记录 → 用户行 → 流水）
        SysUser user = sysUserMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.USER_STATUS_ERROR, "账户状态异常，无法充值");
        }

        // 2. 原子更新余额（单条 SQL：wallet_balance = wallet_balance + ?）
        sysUserMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .setSql("wallet_balance = wallet_balance + {0}", amount));
        SysUser userAfter = sysUserMapper.selectById(userId);

        // 3. 写入资金流水（幂等键保证同键只执行一次，流水随事务唯一）
        WalletLog logRow = new WalletLog();
        logRow.setUserId(userId);
        logRow.setType(WalletLogType.RECHARGE);
        logRow.setAmount(amount);
        logRow.setBalanceAfter(userAfter.getWalletBalance());
        logRow.setRemark("模拟充值");
        walletLogMapper.insert(logRow);

        return new WalletBalanceVO(userAfter.getWalletBalance());
    }

    /** 分页查询资金流水（按时间倒序） */
    public IPage<WalletLog> getLogs(Long userId, int page, int size) {
        LambdaQueryWrapper<WalletLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WalletLog::getUserId, userId)
                    .orderByDesc(WalletLog::getCreatedAt);

        Page<WalletLog> pageParam = new Page<>(page, size);
        return walletLogMapper.selectPage(pageParam, queryWrapper);
    }

    // 死锁重试耗尽由编排层 TradingEntryService 统一转 TRADE_BUSY（不在此处挂 @Recover）
}
