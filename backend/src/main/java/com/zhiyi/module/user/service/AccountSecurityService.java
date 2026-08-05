package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import com.zhiyi.module.user.support.UserStateCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模块一：账号安全 —— 修改密码 / 注销账号（个人中心「账号安全」面板）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountSecurityService {

    private final SysUserMapper userMapper;
    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final PasswordEncoder passwordEncoder;
    private final UserStateCache userStateCache;
    private final LoginAttemptService loginAttemptService;

    /**
     * 修改密码：验证原密码 + 新密码不得与原密码相同。
     * 成功后推进 Token 版本 —— 所有设备（含当前）强制重新登录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, ChangePasswordDTO dto) {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "两次输入的密码不一致");
        }
        // 原密码验证也走失败限流，防止已登录会话被借用后暴力猜原密码
        String lockKey = "chpw:" + userId;
        if (loginAttemptService.isLocked(lockKey)) {
            throw new BusinessException(ResultCode.LOGIN_LOCKED, "原密码错误次数过多，请稍后再试");
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
        userStateCache.invalidateAfterCommit(userId);
        log.info("用户 {} 修改了密码", userId);
    }

    /**
     * 注销账号（软注销）：
     * - 边界 1：有进行中的订单（买/卖任一方 WAITING_MEET）不允许注销；
     * - 边界 2：在售/交易中的商品需先处理 —— 在售商品随注销自动下架；
     * - 边界 3：管理员账户不允许注销；
     * - 学号保留占用（唯一索引仍在），防止他人抢注冒充；
     * - status = CANCELLED + 推进 Token 版本，所有 Token 立即作废。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelAccount(Long userId, CancelAccountDTO dto) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if ("ADMIN".equals(user.getRole())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "管理员账户不允许注销");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR, "密码错误，无法注销");
        }

        // 边界：有进行中的订单（担保资金还挂在平台）禁止注销
        Long activeOrders = orderMapper.selectCount(Wrappers.<TradeOrder>lambdaQuery()
                .eq(TradeOrder::getStatus, "WAITING_MEET")
                .and(w -> w.eq(TradeOrder::getBuyerId, userId).or().eq(TradeOrder::getSellerId, userId)));
        if (activeOrders > 0) {
            throw new BusinessException(ResultCode.CONFLICT,
                    "您还有 " + activeOrders + " 笔进行中的订单，请先完成或取消后再注销");
        }

        // 在售商品随注销自动下架，避免「幽灵商品」滞留大厅
        Item offShelf = new Item();
        offShelf.setStatus("OFF_SHELF");
        itemMapper.update(offShelf, Wrappers.<Item>lambdaUpdate()
                .eq(Item::getPublisherId, userId)
                .eq(Item::getStatus, "ON_SALE"));

        SysUser patch = new SysUser();
        patch.setId(userId);
        patch.setStatus("CANCELLED");
        userMapper.updateById(patch);

        int affected = userMapper.bumpTokenVersion(userId);
        if (affected == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        userStateCache.invalidateAfterCommit(userId);
        log.info("用户 {}（学号 {}）注销了账号", userId, user.getStudentId());
    }
}
