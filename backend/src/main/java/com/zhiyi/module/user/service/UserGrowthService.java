package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.user.entity.ExpLog;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.event.UserLevelUpEvent;
import com.zhiyi.module.user.mapper.ExpLogMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模块一：成长体系（需求 1.5）—— 供全组调用的经验值服务。
 *
 * 对其他模块的契约：
 * - D 模块确认收货事务里：growthService.addExp(buyerId, 50, "买家完成订单")、addExp(sellerId, 50, "卖家完成订单")
 * - B/D 模块强制下架时：growthService.addExp(publisherId, -30, "商品被管理员强制下架")
 *
 * 高并发设计：
 * - exp 用单条 UPDATE 原子增减（DB 端 read-modify-write），并发确认收货不丢加分；
 * - 等级结算基于增减后回读的最新 exp，且只升不降。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrowthService {

    public static final int EXP_ORDER_COMPLETED = 50;    // 买/卖完成一笔订单
    public static final int EXP_FORCED_OFF_SHELF = -30;  // 商品被强制下架

    private final SysUserMapper userMapper;
    private final ExpLogMapper expLogMapper;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 增减经验值并结算等级、记录流水。
     * REQUIRED 传播：若调用方已有事务（如确认收货），加入同一事务一起提交/回滚。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void addExp(Long userId, int delta, String reason) {
        int affected = userMapper.incrExp(userId, delta);
        if (affected == 0) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }

        // 回读最新成长状态，只允许升级；扣经验不会回退已经取得的等级。
        SysUser state = userMapper.selectGrowthState(userId);
        if (state == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        int expAfter = state.getExp();
        int oldLevel = state.getLevel();
        int settledLevel = Math.max(oldLevel, LevelRule.levelOf(expAfter));

        if (settledLevel > oldLevel) {
            SysUser patch = new SysUser();
            patch.setId(userId);
            patch.setLevel(settledLevel);
            userMapper.updateById(patch);
        }

        ExpLog logRow = new ExpLog();
        logRow.setUserId(userId);
        logRow.setDelta(delta);
        logRow.setExpAfter(expAfter);
        logRow.setLevelAfter(settledLevel);
        logRow.setReason(reason);
        expLogMapper.insert(logRow);

        if (settledLevel > oldLevel) {
            eventPublisher.publishEvent(
                    new UserLevelUpEvent(userId, oldLevel, settledLevel, expAfter));
        }

        log.info("用户 {} 经验值 {}{}（{}），当前 exp={} level={}",
                userId, delta > 0 ? "+" : "", delta, reason, expAfter, settledLevel);
    }
}
