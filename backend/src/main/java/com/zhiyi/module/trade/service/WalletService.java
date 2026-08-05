package com.zhiyi.module.trade.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.trade.entity.WalletLog;
import com.zhiyi.module.trade.mapper.WalletLogMapper;
import com.zhiyi.module.trade.vo.WalletBalanceVO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 钱包服务：余额查询、模拟充值、资金流水
 */
@Service
@RequiredArgsConstructor
public class WalletService {

    private final SysUserMapper sysUserMapper;
    private final WalletLogMapper walletLogMapper;

    /**
     * 查询当前用户钱包余额
     */
    public WalletBalanceVO getBalance(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        return new WalletBalanceVO(user.getWalletBalance());
    }

    /**
     * 模拟充值（事务保证：余额更新 + 流水写入，要么都成功要么都回滚）
     */
    @Transactional(rollbackFor = Exception.class)
    public WalletBalanceVO recharge(Long userId, BigDecimal amount) {
        if (userId == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户身份校验失败");
        }
        if (amount == null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "充值金额不能为空");
        }

        // 1. 原子更新余额（单条 SQL：wallet_balance = wallet_balance + ?）
        LambdaUpdateWrapper<SysUser> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.setSql("wallet_balance = wallet_balance + {0}", amount)
                      .eq(SysUser::getId, userId);
        int affected = sysUserMapper.update(null, updateWrapper);
        if (affected == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在或状态异常");
        }

        // 2. 回读最新余额
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 写入资金流水
        WalletLog log = new WalletLog();
        log.setUserId(userId);
        log.setType("RECHARGE");
        log.setAmount(amount);
        log.setBalanceAfter(user.getWalletBalance());
        log.setRemark("模拟充值");
        walletLogMapper.insert(log);

        return new WalletBalanceVO(user.getWalletBalance());
    }

    /**
     * 分页查询资金流水（按时间倒序）
     */
    public IPage<WalletLog> getLogs(Long userId, int page, int size) {
        LambdaQueryWrapper<WalletLog> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(WalletLog::getUserId, userId)
                    .orderByDesc(WalletLog::getCreatedAt);

        Page<WalletLog> pageParam = new Page<>(page, size);
        return walletLogMapper.selectPage(pageParam, queryWrapper);
    }
}
