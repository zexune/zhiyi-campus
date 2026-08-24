package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.BanActionType;
import com.zhiyi.common.enums.OrderCancelReason;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.dto.ConfirmViolationDTO;
import com.zhiyi.module.admin.entity.ViolationLog;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationLogMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.PenaltyStatsVO;
import com.zhiyi.module.admin.vo.ViolationVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.service.ForceCancelService;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 内容审核工作台。
 *
 * 确认违规（B4/I24）：以"买家用户行 + 商品行"为聚合串行点，强制撤销该商品进行中
 * 订单（ADMIN_FORCE + 买家全额退款 + 独立 event_id 通知），再抢占审核记录并重新
 * 投影商品审核状态；REJECTED 商品不得残留 WAITING_MEET 订单。抢占失败时整个事务
 * （含已执行的撤单）一并回滚，无半撤单状态。与确认收货并发时由锁序串行化：
 * 确认收货先行提交则无单可撤（已完成交易不追溯退款）；违规确认先行则随后的
 * 确认收货得 ORDER_STATUS_ERROR。
 *
 * 驳回与申诉路径不涉及撤单，仅做状态抢占 + 投影。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminViolationService {

    private final ViolationReportMapper violationReportMapper;
    private final SysUserMapper sysUserMapper;
    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final ViolationLogMapper violationLogMapper;
    private final ReputationPenaltyService reputationPenaltyService;
    private final ForceCancelService forceCancelService;
    private final ModerationProjectionService projectionService;

    public IPage<ViolationVO> getViolations(int page, int size, String status) {
        IPage<ViolationReport> result = violationReportMapper.selectPage(
                new Page<>(Math.max(1, page), Math.max(1, Math.min(size, 50))),
                violationQuery(status));
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
        // 1. 无锁读报告与活跃订单，仅为确定锁序所需 ID
        ViolationReport report = requirePending(reportId);
        Long itemId = report.getItemId();
        TradeOrder active = itemId == null ? null : orderMapper.selectActiveByItemId(itemId);

        // 2. 锁定买家用户行（锁序：用户 → 商品 → 订单 → 报告）；无挂单则跳过
        if (active != null) {
            sysUserMapper.selectByIdForUpdate(active.getBuyerId());
        }

        // 3. 锁定商品（聚合串行点）
        if (itemId != null) {
            itemMapper.selectByIdForUpdate(itemId);
        }

        // 4. 强制撤单：无条件调用子例程持锁重查（无锁预读在 REPEATABLE READ 下可能
        //     漏掉刚提交的挂单/已完成迁移；商品锁已在本事务手中，子例程以当前读判定）。
        //     撤单、退款、买卖双方独立 event_id 通知与本次审核决定同事务。
        if (itemId != null) {
            forceCancelService.cancelActiveOrderOfItem(itemId, OrderCancelReason.ADMIN_FORCE,
                    "您购买的商品因内容违规被平台强制撤单",
                    "您发布的商品因内容违规被平台强制撤单");
        }

        // 5. 抢占审核记录（失败抛 CONFLICT，整个事务含撤单一并回滚）
        int updated = violationReportMapper.update(null, new LambdaUpdateWrapper<ViolationReport>()
                .eq(ViolationReport::getId, reportId)
                .eq(ViolationReport::getStatus, ViolationStatus.PENDING)
                .set(ViolationReport::getStatus, ViolationStatus.CONFIRMED)
                .set(ViolationReport::getHandlerId, adminId)
                .set(ViolationReport::getHandleNote, trimToNull(dto.getHandleNote()))
                .setSql("handled_at = CURRENT_TIMESTAMP(6)"));
        if (updated == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该记录已被其他管理员处理");
        }

        // 6. 重新投影商品审核状态（REJECTED 时 ON_SALE/RESERVED 一律压到 OFF_SHELF）
        if (itemId != null) {
            projectionService.projectItemModerationStatus(itemId);
        }

        // 7. 记录处罚（DuplicateKeyException 幂等复返，唯一约束竞争不暴露 500）
        reputationPenaltyService.recordContentWarning(
                reportId, report.getUserId(), adminId, dto.getReason().trim());
        log.info("管理员 {} 确认内容违规 reportId={} seller={}（挂单已强制撤销）",
                adminId, reportId, report.getUserId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void dismissViolation(Long reportId, Long adminId) {
        // 1. 无锁读报告取 itemId；锁定商品（聚合串行点）
        ViolationReport report = requirePending(reportId);
        if (report.getItemId() != null) {
            itemMapper.selectByIdForUpdate(report.getItemId());
        }

        // 2. 抢占更新报告状态
        if (violationReportMapper.update(null, new LambdaUpdateWrapper<ViolationReport>()
                .eq(ViolationReport::getId, reportId)
                .eq(ViolationReport::getStatus, ViolationStatus.PENDING)
                .set(ViolationReport::getStatus, ViolationStatus.DISMISSED)
                .set(ViolationReport::getHandlerId, adminId)
                .set(ViolationReport::getHandleNote, "审核未发现违规，予以放行")
                .setSql("handled_at = CURRENT_TIMESTAMP(6)")) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该记录已被其他管理员处理");
        }

        // 3. 重新投影商品审核状态：商品 status 永不自动重新上架，恢复由卖家手动 relist
        if (report.getItemId() != null) {
            projectionService.projectItemModerationStatus(report.getItemId());
        }
        log.info("管理员 {} 放行内容审核 reportId={}", adminId, reportId);
    }

    public PenaltyStatsVO getPenaltyStats(Long userId) {
        PenaltyStatsVO vo = new PenaltyStatsVO();
        vo.setUserId(userId);
        vo.setConfirmedViolations(violationReportMapper.selectCount(
                new LambdaQueryWrapper<ViolationReport>()
                        .eq(ViolationReport::getUserId, userId)
                        .eq(ViolationReport::getStatus, ViolationStatus.CONFIRMED)));
        vo.setWarningCount(reputationPenaltyService.activeWarningCount(userId));
        vo.setBanCount(violationLogMapper.selectCount(
                new LambdaQueryWrapper<ViolationLog>()
                        .eq(ViolationLog::getUserId, userId)
                        .in(ViolationLog::getType, BanActionType.BAN_TEMP, BanActionType.BAN_PERM)));
        vo.setPenaltyScore(reputationPenaltyService.complianceScore(userId));
        return vo;
    }

    private ViolationReport requirePending(Long reportId) {
        ViolationReport report = violationReportMapper.selectById(reportId);
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "审核记录不存在");
        }
        if (report.getStatus() != ViolationStatus.PENDING) {
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
        vo.setSource(report.getSource().code());
        vo.setViolationType(report.getViolationType());
        vo.setViolationReason(report.getViolationReason());
        vo.setMatchedRules(report.getMatchedRules());
        vo.setRuleVersion(report.getRuleVersion());
        vo.setStatus(report.getStatus().code());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandlerName(nameOf(users.get(report.getHandlerId()), null));
        vo.setHandleNote(report.getHandleNote());
        vo.setItemId(report.getItemId());
        Item item = items.get(report.getItemId());
        vo.setItemStatus(item == null ? null : item.getStatus().code());
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

    private LambdaQueryWrapper<ViolationReport> violationQuery(String status) {
        LambdaQueryWrapper<ViolationReport> query = new LambdaQueryWrapper<ViolationReport>()
                .orderByDesc(ViolationReport::getCreatedAt)
                .orderByDesc(ViolationReport::getId);
        if (StringUtils.hasText(status)) {
            try {
                query.eq(ViolationReport::getStatus, ViolationStatus.fromNullable(status));
            } catch (IllegalArgumentException invalidStatus) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "审核状态不合法");
            }
        }
        return query;
    }
}
