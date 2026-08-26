package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.OrderStatus;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模块一：账号安全 —— 修改密码 / 注销账号（个人中心「账号安全」面板）
 *
 * 注销互斥（B5）：先锁定本人用户行再检查进行中订单——下单路径必须先取得
 * 买卖双方用户行锁，因此"持用户锁期间"不可能有新订单产生；
 * 状态迁移为明确 expected state 的条件 UPDATE（ACTIVE → CANCELLED），
 * 同 SQL 推进 token_version，注销不得覆盖已提交的封禁。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private final SysUserMapper userMapper;
    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptService loginAttemptService;

    /**
     * 修改密码：验证原密码 + 新密码不得与原密码相同。
     * 成功后推进 Token 版本 —— 所有设备（含当前）强制重新登录。
     * 失败计数由协调器独立事务提交，业务异常回滚不影响计数。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        // 原密码验证也走失败限流，防止已登录会话被借用后暴力猜原密码
        String lockKey = "chpw:" + userId;
        if (loginAttemptService.isLocked(lockKey)) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED, "原密码错误次数过多，请稍后再试")
                    .withRetryAfterSeconds(loginAttemptService.remainingLockSeconds(lockKey));
        }

        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(lockKey);
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "原密码错误");
        }
        // 约束：新密码不能与原密码相同
        if (passwordEncoder.matches(dto.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.SAME_AS_OLD_PASSWORD);
        }

        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userMapper.updateById(patch);

        int affected = userMapper.bumpTokenVersion(userId);
        if (affected == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        loginAttemptService.reset(lockKey);
        log.info("用户 {} 修改了密码", userId);
    }

    /**
     * 注销账号（软注销）：
     * - 边界 1：有进行中的订单（买/卖任一方 WAITING_MEET）不允许注销；
     * - 边界 2：在售商品随注销自动下架（条件 UPDATE，仅 ON_SALE → OFF_SHELF）；
     * - 边界 3：管理员与 SYSTEM 账户不允许注销；
     * - 学号保留占用（唯一索引仍在），防止他人抢注冒充；
     * - status = CANCELLED + 同 SQL 推进 Token 版本，所有 Token 立即作废。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelAccount(Long userId, CancelAccountDTO dto) {
        // 1. 锁定本人用户行；锁后必须重检 status == ACTIVE
        SysUser user = userMapper.selectByIdForUpdate(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (user.getRole() == UserRole.ADMIN) {
            throw new BusinessException(ResultCode.FORBIDDEN, "管理员账户不允许注销");
        }
        if (Boolean.TRUE.equals(user.getIsSystem())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "SYSTEM 账户不允许注销");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "密码错误，无法注销");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.CONFLICT, "仅正常账户可以注销");
        }

        // 2. 检查进行中订单（持锁期间，下单方因需锁同一用户行而阻塞——互斥成立）
        Long activeOrders = orderMapper.selectCount(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getStatus, OrderStatus.WAITING_MEET)
                .and(w -> w.eq(TradeOrder::getBuyerId, userId).or().eq(TradeOrder::getSellerId, userId)));
        if (activeOrders > 0) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "您还有 " + activeOrders + " 笔进行中的订单，请先完成或取消后再注销");
        }

        // 3. 在售商品随注销自动下架（条件 UPDATE，仅 ON_SALE → OFF_SHELF）
        itemMapper.update(null, new LambdaUpdateWrapper<Item>()
                .eq(Item::getPublisherId, userId)
                .eq(Item::getStatus, ItemStatus.ON_SALE)
                .set(Item::getStatus, ItemStatus.OFF_SHELF));

        // 4. 标记注销 + 推进 Token 版本（原子；expected state 拒绝覆盖封禁等已提交状态）
        int updated = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getStatus, UserStatus.ACTIVE)
                .set(SysUser::getStatus, UserStatus.CANCELLED)
                .setSql("token_version = token_version + 1"));
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "账户状态已变更");
        }

        log.info("用户 {}（学号 {}）注销了账号", userId, user.getStudentId());
    }
}
