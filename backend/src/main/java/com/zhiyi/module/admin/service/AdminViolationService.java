package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.module.admin.dto.ConfirmViolationDTO;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.PenaltyStatsVO;
import com.zhiyi.module.admin.vo.ViolationVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容审核工作台。内容违规只下架并执行固定警告扣分，账号封禁由用户管理独立完成。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminViolationService {

    private final ViolationReportMapper violationReportMapper;
    private final SysUserMapper sysUserMapper;
    private final ItemMapper itemMapper;
    private final ViolationLogMapper violationLogMapper;
    private final ReputationPenaltyService reputationPenaltyService;

    public IPage<ViolationVO> getViolations(int page, int size, String status) {
        IPage<ViolationReport> result = violationReportMapper.selectPage(
                new Page<>(Math.max(1, page), Math.max(1, Math.min(size, 50))),
                new LambdaQueryWrapper<ViolationReport>()
                        .eq(StringUtils.hasText(status), ViolationReport::getStatus, status)
                        .orderByDesc(ViolationReport::getCreatedAt));
        List<ViolationReport> records = result.getRecords();
        if (records.isEmpty()) {
            return result.convert(report -> toVO(report, Map.of(), Map.of()));
        }

        Set<Long> userIds = new HashSet<>();
        Set<Long> itemIds = new HashSet<>();
        for (ViolationReport report : records) {
            if (report.getUserId() != null) userIds.add(report.getUserId());
            if (report.getReporterId() != null) userIds.add(report.getReporterId());
            if (report.getHandlerId() != null) userIds.add(report.getHandlerId());
            if (report.getItemId() != null) itemIds.add(report.getItemId());
        }
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        Map<Long, Item> items = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        return result.convert(report -> toVO(report, users, items));
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmViolation(Long reportId, ConfirmViolationDTO dto, Long adminId) {
        ViolationReport report = requirePending(reportId);
        LocalDateTime now = LocalDateTime.now();
        if (violationReportMapper.update(null, new LambdaUpdateWrapper<ViolationReport>()
                .eq(ViolationReport::getId, reportId)
                .eq(ViolationReport::getStatus, "PENDING")
                .set(ViolationReport::getStatus, "CONFIRMED")
                .set(ViolationReport::getHandlerId, adminId)
                .set(ViolationReport::getHandleNote, trimToNull(dto.getHandleNote()))
                .set(ViolationReport::getHandledAt, now)) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该记录已被其他管理员处理");
        }

        Item item = report.getItemId() == null ? null : itemMapper.selectById(report.getItemId());
        if (item != null) {
            item.setModerationStatus("REJECTED");
            if (!"SOLD".equals(item.getStatus())) {
                item.setStatus("OFF_SHELF");
            }
            itemMapper.updateById(item);
        }
        reputationPenaltyService.recordContentWarning(
                reportId, report.getUserId(), adminId, dto.getReason().trim());
        log.info("管理员 {} 确认内容违规 reportId={} seller={}", adminId, reportId, report.getUserId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void dismissViolation(Long reportId, Long adminId) {
        ViolationReport report = requirePending(reportId);
        if (violationReportMapper.update(null, new LambdaUpdateWrapper<ViolationReport>()
                .eq(ViolationReport::getId, reportId)
                .eq(ViolationReport::getStatus, "PENDING")
                .set(ViolationReport::getStatus, "DISMISSED")
                .set(ViolationReport::getHandlerId, adminId)
                .set(ViolationReport::getHandleNote, "审核未发现违规，予以放行")
                .set(ViolationReport::getHandledAt, LocalDateTime.now())) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该记录已被其他管理员处理");
        }

        if (!"USER_REPORT".equals(report.getSource()) && report.getItemId() != null) {
            Item item = itemMapper.selectById(report.getItemId());
            if (item != null && !"SOLD".equals(item.getStatus())) {
                item.setModerationStatus("PASSED");
                item.setStatus("ON_SALE");
                itemMapper.updateById(item);
            }
        }
        log.info("管理员 {} 放行内容审核 reportId={}", adminId, reportId);
    }

    public PenaltyStatsVO getPenaltyStats(Long userId) {
        PenaltyStatsVO vo = new PenaltyStatsVO();
        vo.setUserId(userId);
        vo.setConfirmedViolations(violationReportMapper.selectCount(
                new LambdaQueryWrapper<ViolationReport>()
                        .eq(ViolationReport::getUserId, userId)
                        .eq(ViolationReport::getStatus, "CONFIRMED")));
        vo.setWarningCount(reputationPenaltyService.activeWarningCount(userId));
        vo.setBanCount(violationLogMapper.selectCount(
                new LambdaQueryWrapper<ViolationLog>()
                        .eq(ViolationLog::getUserId, userId)
                        .in(ViolationLog::getType, "BAN_TEMP", "BAN_PERM")));
        vo.setPenaltyScore(reputationPenaltyService.complianceScore(userId));
        return vo;
    }

    private ViolationReport requirePending(Long reportId) {
        ViolationReport report = violationReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审核记录不存在");
        }
        if (!"PENDING".equals(report.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该审核记录已处理");
        }
        return report;
    }

    private ViolationVO toVO(ViolationReport report,
                             Map<Long, SysUser> users,
                             Map<Long, Item> items) {
        ViolationVO vo = new ViolationVO();
        vo.setId(report.getId());
        vo.setUserId(report.getUserId());
        vo.setSellerName(nameOf(users.get(report.getUserId()), "未知卖家"));
        vo.setReporterId(report.getReporterId());
        vo.setReporterName(nameOf(users.get(report.getReporterId()), null));
        vo.setOriginalTitle(report.getOriginalTitle());
        vo.setOriginalDescription(report.getOriginalDescription());
        vo.setSource(report.getSource());
        vo.setViolationType(report.getViolationType());
        vo.setViolationReason(report.getViolationReason());
        vo.setMatchedRules(report.getMatchedRules());
        vo.setRuleVersion(report.getRuleVersion());
        vo.setStatus(report.getStatus());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandlerName(nameOf(users.get(report.getHandlerId()), null));
        vo.setHandleNote(report.getHandleNote());
        vo.setItemId(report.getItemId());
        Item item = items.get(report.getItemId());
        vo.setItemStatus(item == null ? null : item.getStatus());
        vo.setCreatedAt(report.getCreatedAt());
        vo.setHandledAt(report.getHandledAt());
        return vo;
    }

    private String nameOf(SysUser user, String fallback) {
        return user == null ? fallback : user.getNickname();
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
