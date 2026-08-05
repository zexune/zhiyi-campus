package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.zhiyi.module.user.dto.BanUserDTO;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.BanService;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 超管违规审核服务 —— 4.5 人工审核与风控工作台
 *
 * 确认违规 → 处罚用户（调 A 模块 BanService）并写入独立信誉处罚
 * 误判放行 → 标记报告为 DISMISSED，并将关联商品重新上架
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminViolationService {

    private final ViolationReportMapper violationReportMapper;
    private final SysUserMapper sysUserMapper;
    private final ItemMapper itemMapper;
    private final BanService banService;
    private final ViolationLogMapper violationLogMapper;
    private final ReputationPenaltyService reputationPenaltyService;

    /**
     * 分页查询违规记录列表
     */
    public IPage<ViolationVO> getViolations(int page, int size, String status) {
        LambdaQueryWrapper<ViolationReport> q = new LambdaQueryWrapper<ViolationReport>()
                .eq(status != null && !status.isEmpty(), ViolationReport::getStatus, status)
                .orderByDesc(ViolationReport::getCreatedAt);

        Page<ViolationReport> p = new Page<>(page, size);
        IPage<ViolationReport> result = violationReportMapper.selectPage(p, q);

        List<ViolationReport> records = result.getRecords();
        if (records.isEmpty()) {
            return result.convert(r -> toVO(r, Map.of(), Map.of(), Map.of()));
        }

        // 批量预加载：收集所有 userId、handlerId、itemId
        List<Long> userIds = records.stream().map(ViolationReport::getUserId)
                .distinct().collect(Collectors.toList());
        List<Long> handlerIds = records.stream().map(ViolationReport::getHandlerId)
                .filter(id -> id != null).distinct().collect(Collectors.toList());
        List<Long> itemIds = records.stream().map(ViolationReport::getItemId)
                .filter(id -> id != null).distinct().collect(Collectors.toList());

        Map<Long, SysUser> userMap = userIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, SysUser> handlerMap = handlerIds.isEmpty() ? Map.of()
                : sysUserMapper.selectBatchIds(handlerIds).stream()
                        .collect(Collectors.toMap(SysUser::getId, u -> u));
        Map<Long, Item> itemMap = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectBatchIds(itemIds).stream()
                        .collect(Collectors.toMap(Item::getId, i -> i));

        // 合并 handler 到 userMap（避免 key 冲突，用不同的 map 更安全）
        final Map<Long, SysUser> finalUserMap = userMap;
        final Map<Long, SysUser> finalHandlerMap = handlerMap;
        final Map<Long, Item> finalItemMap = itemMap;

        return result.convert(r -> toVO(r, finalUserMap, finalHandlerMap, finalItemMap));
    }

    /**
     * 确认违规 + 处罚用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void confirmViolation(Long reportId, ConfirmViolationDTO dto, Long adminId) {
        ViolationReport report = violationReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "违规记录不存在");
        }
        if (!"PENDING".equals(report.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该违规记录已处理，不能重复操作");
        }

        // 1. 更新违规记录
        report.setStatus("CONFIRMED");
        report.setHandlerId(adminId);
        report.setHandleNote(dto.getHandleNote());
        report.setHandledAt(LocalDateTime.now());
        violationReportMapper.updateById(report);

        // 2. 调用 A 模块封禁服务
        BanUserDTO banDTO = new BanUserDTO();
        banDTO.setUserId(report.getUserId());
        banDTO.setType(dto.getType());
        banDTO.setReason(dto.getReason());
        banDTO.setBanDays(dto.getBanDays());
        banService.punish(banDTO, adminId);

        // 3. 独立信誉处罚：不修改或占用真实交易评价
        reputationPenaltyService.recordPenalty(
                report.getId(), report.getUserId(), adminId, dto.getType(), dto.getReason());

        log.info("管理员 {} 确认违规 reportId={}, 处罚用户 {} type={}", adminId, reportId, report.getUserId(), dto.getType());
    }

    /**
     * 误判放行 —— 撤销违规记录，商品重新上架
     */
    @Transactional(rollbackFor = Exception.class)
    public void dismissViolation(Long reportId, Long adminId) {
        ViolationReport report = violationReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "违规记录不存在");
        }
        if (!"PENDING".equals(report.getStatus())) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该违规记录已处理，不能重复操作");
        }

        // 1. 更新违规记录
        report.setStatus("DISMISSED");
        report.setHandlerId(adminId);
        report.setHandleNote("AI 误判，予以放行");
        report.setHandledAt(LocalDateTime.now());
        violationReportMapper.updateById(report);

        // 2. 商品重新上架
        if (report.getItemId() != null) {
            Item item = itemMapper.selectById(report.getItemId());
            if (item != null && "OFF_SHELF".equals(item.getStatus())) {
                item.setStatus("ON_SALE");
                item.setAiReviewed(true);
                itemMapper.updateById(item);
                log.info("商品 {} 已重新上架", item.getId());
            }
        }

        log.info("管理员 {} 驳回违规 reportId={}（误判放行），发布者 userId={}", adminId, reportId, report.getUserId());
    }

    // ---- 内部工具 ----

    private ViolationVO toVO(ViolationReport r, Map<Long, SysUser> userMap,
                              Map<Long, SysUser> handlerMap, Map<Long, Item> itemMap) {
        ViolationVO vo = new ViolationVO();
        vo.setId(r.getId());
        vo.setUserId(r.getUserId());
        vo.setOriginalTitle(r.getOriginalTitle());
        vo.setOriginalDescription(r.getOriginalDescription());
        vo.setViolationType(r.getViolationType());
        vo.setViolationReason(r.getViolationReason());
        vo.setAiTags(r.getAiTags());
        vo.setStatus(r.getStatus());
        vo.setHandlerId(r.getHandlerId());
        vo.setHandleNote(r.getHandleNote());
        vo.setItemId(r.getItemId());
        vo.setAiReviewError(r.getAiReviewError());
        vo.setCreatedAt(r.getCreatedAt());
        vo.setHandledAt(r.getHandledAt());

        // 商品状态（从预加载的 map 取，避免 N+1）
        if (r.getItemId() != null) {
            Item item = itemMap.get(r.getItemId());
            vo.setItemStatus(item != null ? item.getStatus() : null);
        }

        // 发布者昵称
        if (r.getUserId() != null) {
            SysUser reporter = userMap.get(r.getUserId());
            vo.setReporterName(reporter != null ? reporter.getNickname() : "未知用户");
        } else {
            vo.setReporterName("未知用户");
        }

        // 处理管理员昵称
        if (r.getHandlerId() != null) {
            SysUser handler = handlerMap.get(r.getHandlerId());
            vo.setHandlerName(handler != null ? handler.getNickname() : null);
        }

        return vo;
    }

    /**
     * 用户处罚评分统计（D4：独立信誉处罚）
     *
     * CONFIRMED 报告用于统计确认次数，DISMISSED 报告不产生处罚记录。
     * 合规度统一从独立信誉处罚记录聚合，警告/封禁次数仍从处罚日志统计。
     */
    public PenaltyStatsVO getPenaltyStats(Long userId) {
        PenaltyStatsVO vo = new PenaltyStatsVO();
        vo.setUserId(userId);

        // CONFIRMED 违规 = 有效处罚
        long confirmedCount = violationReportMapper.selectCount(
                new LambdaQueryWrapper<ViolationReport>()
                        .eq(ViolationReport::getUserId, userId)
                        .eq(ViolationReport::getStatus, "CONFIRMED"));
        vo.setConfirmedViolations(confirmedCount);

        // 处罚日志中的警告和封禁次数
        long warningCount = violationLogMapper.selectCount(
                new LambdaQueryWrapper<ViolationLog>()
                        .eq(ViolationLog::getUserId, userId)
                        .eq(ViolationLog::getType, "WARNING"));
        vo.setWarningCount(warningCount);

        long banCount = violationLogMapper.selectCount(
                new LambdaQueryWrapper<ViolationLog>()
                        .eq(ViolationLog::getUserId, userId)
                        .in(ViolationLog::getType, "BAN_TEMP", "BAN_PERM"));
        vo.setBanCount(banCount);

        // 与用户信誉雷达的“合规度”使用同一套独立处罚记录。
        vo.setPenaltyScore(reputationPenaltyService.complianceScore(userId));

        return vo;
    }

}
