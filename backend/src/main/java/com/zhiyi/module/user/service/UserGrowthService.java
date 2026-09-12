package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.social.service.OutboxService;
import com.zhiyi.module.user.entity.ExpLog;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.ExpLogMapper;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.ExpRule;
import com.zhiyi.module.user.support.LevelRule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 成长体系经验引擎 —— 按 {@link ExpRule} 目录发放经验，全组统一入口。
 *
 * 并发与一致性：
 * - exp 用单条 UPDATE 原子增减（DB 端 read-modify-write），并发不丢加分；
 * - 等级结算基于增减后回读的最新 exp，只升不降（扣经验不回退已取得等级）；
 * - 一次性任务先查后插，并发窗口由 uk_exp_dedup(user_id, dedup_key) 兜底：
 *   理论竞态下 DuplicateKey 被吞掉并记日志，最坏结果是多发一次小分值经验
 *  （非资金指标，无不变量破坏）；调用方事务不受影响；
 * - 每日上限为当日已发放条数的事务内计数，极端并发可能超发一条——经验非权威
 *   指标，不为它引入额外串行化成本。
 *
 * 升级系统消息通过事务 Outbox 与业务同事务写入（B3/M7）：业务回滚时消息随之
 * 消失，提交后由消费者至少一次投递。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserGrowthService {

    private final SysUserMapper userMapper;
    private final ExpLogMapper expLogMapper;
    private final OutboxService outboxService;

    /**
     * 按目录规则发放经验（无衰减）。
     * REQUIRED 传播：加入调用方事务一起提交/回滚。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void award(Long userId, ExpRule rule) {
        award(userId, rule, 1.0, null);
    }

    /**
     * 按目录规则发放经验，附带收益系数（{@link com.zhiyi.module.user.support.PairDecayRule}）
     * 与流水附注（如"第 3 次回购"）。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void award(Long userId, ExpRule rule, double factor, String note) {
        if (rule.oneTime() && oneTimeAlreadyClaimed(userId, rule)) {
            return;
        }
        if (rule.dailyCap() > 0 && countAwardedToday(userId, rule) >= rule.dailyCap()) {
            return;
        }
        int points = (int) Math.round(rule.points() * factor);
        if (points <= 0) {
            return;
        }
        String reason = note == null || note.isBlank()
                ? rule.label()
                : rule.label() + "（" + note + "）";
        addExp(userId, points, reason, rule.oneTime() ? rule.dedupKey() : null, rule.name());
    }

    /**
     * 底层增减经验并结算等级、记录流水（供本类与既有调用方使用）。
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void addExp(Long userId, int delta, String reason) {
        addExp(userId, delta, reason, null, null);
    }

    private void addExp(Long userId, int delta, String reason, String dedupKey, String ruleCode) {
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
        logRow.setRuleCode(ruleCode);
        logRow.setDedupKey(dedupKey);
        try {
            expLogMapper.insert(logRow);
        } catch (DuplicateKeyException alreadyClaimed) {
            // 一次性任务并发竞态（先查后插窗口）：本次经验已加但流水被唯一键拒绝。
            // 理论不可达路径（调用方均有自身串行化），最坏超发一次小分值经验，不回滚调用方业务。
            log.warn("一次性经验任务并发重复领取被唯一键拦截 userId={} rule={}", userId, ruleCode);
        }

        if (settledLevel > oldLevel) {
            outboxService.appendNotice("USER:" + userId + ":LEVEL_UP:" + settledLevel,
                    OutboxService.AGGREGATE_USER, userId, OutboxService.EVENT_USER_LEVEL_UP,
                    userId, "恭喜升级到 Lv." + settledLevel + "，当前经验 " + expAfter
                            + "。继续保持靠谱交易记录。");
        }

        log.info("用户 {} 经验值 {}{}（{}），当前 exp={} level={}",
                userId, delta > 0 ? "+" : "", delta, reason, expAfter, settledLevel);
    }

    private boolean oneTimeAlreadyClaimed(Long userId, ExpRule rule) {
        return expLogMapper.selectCount(Wrappers.<ExpLog>lambdaQuery()
                .eq(ExpLog::getUserId, userId)
                .eq(ExpLog::getDedupKey, rule.dedupKey())) > 0;
    }

    /** 今日（数据库会话时区当日）已按该规则发放的条数。 */
    private long countAwardedToday(Long userId, ExpRule rule) {
        return expLogMapper.selectCount(Wrappers.<ExpLog>lambdaQuery()
                .eq(ExpLog::getUserId, userId)
                .eq(ExpLog::getRuleCode, rule.name())
                .ge(ExpLog::getCreatedAt, LocalDate.now().atStartOfDay()));
    }
}
