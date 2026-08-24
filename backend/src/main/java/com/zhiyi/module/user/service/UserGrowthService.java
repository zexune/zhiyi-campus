package com.zhiyi.module.user.service;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.user.entity.ExpLog;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.ExpLogMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模块一：成长体系（需求 1.5）—— 供全组调用的经验值服务。
 *
 * 高并发设计：
 * - exp 用单条 UPDATE 原子增减（DB 端 read-modify-write），并发确认收货不丢加分；
 * - 等级结算基于增减后回读的最新 exp，且只升不降；
 * - 升级系统消息通过事务 Outbox 与业务同事务写入（B3/M7 修复）：
 *   业务回滚时消息随之消失，提交后由消费者至少一次投递，
 *   不再使用 AFTER_COMMIT → REQUIRES_NEW 的连锁失败路径。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrowthService {

    public static final int EXP_ORDER_COMPLETED = 50;    // 买/卖完成一笔订单

    private final SysUserMapper userMapper;
    private final ExpLogMapper expLogMapper;
    private final OutboxService outboxService;

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
            outboxService.appendNotice("USER:" + userId + ":LEVEL_UP:" + settledLevel,
                    OutboxService.AGGREGATE_USER, userId, OutboxService.EVENT_USER_LEVEL_UP,
                    userId, "恭喜升级到 Lv." + settledLevel + "，当前经验 " + expAfter
                            + "。继续保持靠谱交易记录。");
        }

        log.info("用户 {} 经验值 {}{}（{}），当前 exp={} level={}",
                userId, delta > 0 ? "+" : "", delta, reason, expAfter, settledLevel);
    }
}
