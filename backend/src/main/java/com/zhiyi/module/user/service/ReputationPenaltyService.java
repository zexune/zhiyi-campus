package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhiyi.module.user.entity.ReputationPenalty;
import com.zhiyi.module.user.mapper.ReputationPenaltyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 信誉处罚策略：处罚作为独立维度参与信誉计算，不污染买家的真实交易评价。
 */
@Service
@RequiredArgsConstructor
public class ReputationPenaltyService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";
    public static final String TYPE_CONTENT_WARNING = "CONTENT_WARNING";

    private final ReputationPenaltyMapper penaltyMapper;

    @Value("${zhiyi.moderation.warning-points:5}")
    private int warningPoints = 5;

    /** 同一违规审核记录只生成一条固定内容警告。 */
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
        penalty.setType(TYPE_CONTENT_WARNING);
        penalty.setPoints(Math.max(1, warningPoints));
        penalty.setReason(reason);
        penalty.setStatus(STATUS_ACTIVE);
        penaltyMapper.insert(penalty);
        return penalty;
    }

    /** 申诉通过时幂等撤销原扣分，避免重试造成重复返分。 */
    public boolean revokePenalty(Long reportId) {
        int updated = penaltyMapper.update(null, new LambdaUpdateWrapper<ReputationPenalty>()
                .eq(ReputationPenalty::getReportId, reportId)
                .eq(ReputationPenalty::getStatus, STATUS_ACTIVE)
                .set(ReputationPenalty::getStatus, STATUS_REVOKED)
                .set(ReputationPenalty::getRevokedAt, LocalDateTime.now()));
        return updated > 0;
    }

    public long activeWarningCount(Long userId) {
        return penaltyMapper.selectCount(new LambdaQueryWrapper<ReputationPenalty>()
                .eq(ReputationPenalty::getUserId, userId)
                .eq(ReputationPenalty::getType, TYPE_CONTENT_WARNING)
                .eq(ReputationPenalty::getStatus, STATUS_ACTIVE));
    }

    /** 当前有效处罚的累计扣分。 */
    public int activePenaltyPoints(Long userId) {
        List<ReputationPenalty> penalties = penaltyMapper.selectList(
                new LambdaQueryWrapper<ReputationPenalty>()
                        .eq(ReputationPenalty::getUserId, userId)
                        .eq(ReputationPenalty::getStatus, STATUS_ACTIVE));
        long total = penalties.stream()
                .map(ReputationPenalty::getPoints)
                .filter(points -> points != null && points > 0)
                .mapToLong(Integer::longValue)
                .sum();
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    /** 合规度越高越好：无有效处罚为 100，累计扣分后最低为 0。 */
    public int complianceScore(Long userId) {
        return Math.max(0, 100 - activePenaltyPoints(userId));
    }

}
