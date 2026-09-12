package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.module.user.entity.ReputationPenalty;
import com.zhiyi.common.enums.PenaltyStatus;
import com.zhiyi.common.enums.PenaltyType;
import com.zhiyi.module.user.mapper.ReputationPenaltyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信誉处罚策略：处罚作为独立维度参与信誉计算，不污染买家的真实交易评价。
 *
 * 扣分随时间线性衰减（默认 180 天归零）：处罚反映"近期合规"而非终身污点，
 * 满窗口后自然失效（无需后台任务清理，查询时按 created_at 现算）；
 * 申诉通过仍然立即全额撤销。撤销与衰减互不冲突：撤销是权利救济，衰减是时间治愈。
 */
@Service
@RequiredArgsConstructor
public class ReputationPenaltyService {

    private final ReputationPenaltyMapper penaltyMapper;

    @Value("${zhiyi.moderation.warning-points:5}")
    private int warningPoints = 5;

    /** 处罚扣分线性衰减窗口（天） */
    @Value("${zhiyi.moderation.penalty-decay-days:180}")
    private int penaltyDecayDays = 180;

    /** 衰减窗口的实际配置值（供归因文案等展示侧引用，避免硬编码第二份数字） */
    public int penaltyDecayDays() {
        return penaltyDecayDays;
    }

    /** 同一违规审核记录只生成一条固定内容警告（先查后插竞争由唯一约束兜底，DuplicateKey 幂等复返）。 */
    public ReputationPenalty recordContentWarning(Long reportId, Long userId, Long adminId,
                                                    String reason) {
        ReputationPenalty existing = penaltyMapper.selectOne(
                new LambdaQueryWrapper<ReputationPenalty>()
                        .eq(ReputationPenalty::getReportId, reportId));
        if (existing != null) {
            return existing;
        }

        ReputationPenalty penalty = new ReputationPenalty();
        penalty.setReportId(reportId);
        penalty.setUserId(userId);
        penalty.setAdminId(adminId);
        penalty.setType(PenaltyType.CONTENT_WARNING);
        penalty.setPoints(Math.max(1, warningPoints));
        penalty.setReason(reason);
        penalty.setStatus(PenaltyStatus.ACTIVE);
        try {
            penaltyMapper.insert(penalty);
        } catch (DuplicateKeyException concurrentInsert) {
            // 并发确认同一报告时 uk_reputation_penalty_report 竞争：复返已有记录，
            // 不把数据库唯一约束竞争暴露成 500。
            return penaltyMapper.selectOne(new LambdaQueryWrapper<ReputationPenalty>()
                    .eq(ReputationPenalty::getReportId, reportId));
        }
        return penalty;
    }

    /** 申诉通过时幂等撤销原扣分，避免重试造成重复返分。 */
    public boolean revokePenalty(Long reportId) {
        int updated = penaltyMapper.update(null, new LambdaUpdateWrapper<ReputationPenalty>()
                .eq(ReputationPenalty::getReportId, reportId)
                .eq(ReputationPenalty::getStatus, PenaltyStatus.ACTIVE)
                .set(ReputationPenalty::getStatus, PenaltyStatus.REVOKED)
                .set(ReputationPenalty::getRevokedAt, LocalDateTime.now()));
        return updated > 0;
    }

    public long activeWarningCount(Long userId) {
        return penaltyMapper.selectCount(new LambdaQueryWrapper<ReputationPenalty>()
                .eq(ReputationPenalty::getUserId, userId)
                .eq(ReputationPenalty::getType, PenaltyType.CONTENT_WARNING)
                .eq(ReputationPenalty::getStatus, PenaltyStatus.ACTIVE));
    }

    /** 当前有效处罚的累计扣分（已按时间衰减）。 */
    public int activePenaltyPoints(Long userId) {
        return (int) Math.round(activePenaltiesWithDecay(userId).stream()
                .mapToDouble(EffectivePenalty::effectivePoints)
                .sum());
    }

    /** 合规度越高越好：无有效处罚为 100，累计扣分后最低为 0。 */
    public int complianceScore(Long userId) {
        return Math.max(0, 100 - activePenaltyPoints(userId));
    }

    /**
     * 有效处罚及其衰减后扣分：points × max(0, 1 - 年龄/衰减窗口)。
     * 过窗处罚保留记录但不再计入（自然失效）；撤销（REVOKED）不在此列。
     */
    public List<EffectivePenalty> activePenaltiesWithDecay(Long userId) {
        List<ReputationPenalty> penalties = penaltyMapper.selectList(
                new LambdaQueryWrapper<ReputationPenalty>()
                        .eq(ReputationPenalty::getUserId, userId)
                        .eq(ReputationPenalty::getStatus, PenaltyStatus.ACTIVE));
        LocalDateTime now = LocalDateTime.now();
        return penalties.stream()
                .filter(penalty -> penalty.getPoints() != null && penalty.getPoints() > 0)
                .map(penalty -> new EffectivePenalty(
                        penalty.getId(),
                        effectivePoints(penalty, now),
                        penalty.getCreatedAt()))
                .filter(penalty -> penalty.effectivePoints() > 0)
                .toList();
    }

    private int effectivePoints(ReputationPenalty penalty, LocalDateTime now) {
        if (penalty.getCreatedAt() == null) {
            // 防御分支：缺失时间戳按未衰减全额计（保守口径，正常数据不会到达）
            return penalty.getPoints();
        }
        long ageDays = java.time.Duration.between(penalty.getCreatedAt(), now).toDays();
        double factor = Math.max(0.0, 1.0 - (double) ageDays / penaltyDecayDays);
        return (int) Math.round(penalty.getPoints() * factor);
    }

    /** 带衰减后有效扣分的处罚明细（供信誉雷达归因展示）。 */
    public record EffectivePenalty(long penaltyId, int effectivePoints, LocalDateTime createdAt) {
    }

}
