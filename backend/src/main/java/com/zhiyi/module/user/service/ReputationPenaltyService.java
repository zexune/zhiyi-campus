package com.zhiyi.module.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.user.entity.ReputationPenalty;
import com.zhiyi.module.user.mapper.ReputationPenaltyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

/**
 * 信誉处罚策略：处罚作为独立维度参与信誉计算，不污染买家的真实交易评价。
 */
@Service
@RequiredArgsConstructor
public class ReputationPenaltyService {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REVOKED = "REVOKED";

    private static final int WARNING_POINTS = 5;
    private static final int TEMP_BAN_POINTS = 15;
    private static final int PERM_BAN_POINTS = 40;

    private final ReputationPenaltyMapper penaltyMapper;

    /** 同一违规报告只生成一条信誉处罚记录。 */
    public ReputationPenalty recordPenalty(Long reportId, Long userId, Long adminId,
                                             String type, String reason) {
        ReputationPenalty existing = penaltyMapper.selectOne(
                new LambdaQueryWrapper<ReputationPenalty>()
                        .eq(ReputationPenalty::getReportId, reportId));
        if (existing != null) {
            return existing;
        }

        String normalizedType = normalizeType(type);
        ReputationPenalty penalty = new ReputationPenalty();
        penalty.setReportId(reportId);
        penalty.setUserId(userId);
        penalty.setAdminId(adminId);
        penalty.setType(normalizedType);
        penalty.setPoints(pointsFor(normalizedType));
        penalty.setReason(reason);
        penalty.setStatus(STATUS_ACTIVE);
        penaltyMapper.insert(penalty);
        return penalty;
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

    private String normalizeType(String type) {
        String normalized = type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
        if (!List.of("WARNING", "BAN_TEMP", "BAN_PERM").contains(normalized)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "不支持的处罚类型");
        }
        return normalized;
    }

    private int pointsFor(String type) {
        return switch (type) {
            case "WARNING" -> WARNING_POINTS;
            case "BAN_TEMP" -> TEMP_BAN_POINTS;
            case "BAN_PERM" -> PERM_BAN_POINTS;
            default -> throw new IllegalArgumentException("Unsupported penalty type: " + type);
        };
    }
}
