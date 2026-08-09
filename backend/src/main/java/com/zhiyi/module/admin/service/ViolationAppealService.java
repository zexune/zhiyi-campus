package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.enums.AppealStatus;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.ModerationStatus;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.dto.HandleAppealDTO;
import com.zhiyi.module.admin.dto.SubmitAppealDTO;
import com.zhiyi.module.admin.entity.ViolationAppeal;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationAppealMapper;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.AppealVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.item.service.TagQueryService;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.service.ReputationPenaltyService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ViolationAppealService {

    private final ViolationAppealMapper appealMapper;
    private final ViolationReportMapper reportMapper;
    private final ItemMapper itemMapper;
    private final SysUserMapper userMapper;
    private final ReputationPenaltyService penaltyService;
    private final TagQueryService tagQueryService;

    @Value("${zhiyi.moderation.appeal-window-days:7}")
    private int appealWindowDays = 7;

    @Transactional
    public AppealVO submit(Long userId, Long reportId, SubmitAppealDTO dto) {
        ViolationReport report = requireReport(reportId);
        if (!report.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能申诉自己的商品处罚");
        }
        if (report.getStatus() != ViolationStatus.CONFIRMED) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "只有已确认的违规记录可以申诉");
        }
        LocalDateTime deadline = report.getHandledAt() == null
                ? null
                : report.getHandledAt().plusDays(Math.max(1, appealWindowDays));
        if (deadline == null || LocalDateTime.now().isAfter(deadline)) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "申诉期限已过");
        }
        if (appealMapper.selectCount(new LambdaQueryWrapper<ViolationAppeal>()
                .eq(ViolationAppeal::getReportId, reportId)) > 0) {
            throw new BusinessException(ResultCode.CONFLICT, "该次违规判定已经申诉过");
        }

        ViolationAppeal appeal = new ViolationAppeal();
        appeal.setReportId(reportId);
        appeal.setItemId(report.getItemId());
        appeal.setUserId(userId);
        appeal.setReason(dto.reason().trim());
        appeal.setStatus(AppealStatus.PENDING);
        try {
            appealMapper.insert(appeal);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(ResultCode.CONFLICT, "该次违规判定已经申诉过");
        }
        return assemble(List.of(appeal), Map.of(report.getId(), report)).getFirst();
    }

    @Transactional
    public AppealVO submitLatestForItem(Long userId, Long itemId, SubmitAppealDTO dto) {
        ViolationReport report = reportMapper.selectOne(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, itemId)
                .eq(ViolationReport::getUserId, userId)
                .eq(ViolationReport::getStatus, ViolationStatus.CONFIRMED)
                .orderByDesc(ViolationReport::getHandledAt)
                .orderByDesc(ViolationReport::getId)
                .last("LIMIT 1"));
        if (report == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "没有可申诉的违规判定");
        }
        return submit(userId, report.getId(), dto);
    }

    public IPage<AppealVO> list(int page, int size, String status) {
        LambdaQueryWrapper<ViolationAppeal> query = new LambdaQueryWrapper<ViolationAppeal>()
                .orderByDesc(ViolationAppeal::getCreatedAt)
                .orderByDesc(ViolationAppeal::getId);
        if (StringUtils.hasText(status)) {
            try {
                query.eq(ViolationAppeal::getStatus, AppealStatus.fromNullable(status));
            } catch (IllegalArgumentException invalidStatus) {
                throw new BusinessException(ResultCode.BAD_REQUEST, "申诉状态不合法");
            }
        }
        IPage<ViolationAppeal> appeals = appealMapper.selectPage(
                new Page<>(Math.max(1, page), Math.max(1, Math.min(size, 50))), query);
        Set<Long> reportIds = appeals.getRecords().stream().map(ViolationAppeal::getReportId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ViolationReport> reports = reportIds.isEmpty() ? Map.of()
                : reportMapper.selectByIds(reportIds).stream()
                .collect(Collectors.toMap(ViolationReport::getId, Function.identity()));
        Page<AppealVO> result = new Page<>(appeals.getCurrent(), appeals.getSize(), appeals.getTotal());
        result.setRecords(assemble(appeals.getRecords(), reports));
        return result;
    }

    @Transactional
    public void approve(Long appealId, Long adminId, HandleAppealDTO dto) {
        ViolationAppeal appeal = requirePendingAppeal(appealId);
        LocalDateTime handledAt = LocalDateTime.now();
        if (appealMapper.update(null, new LambdaUpdateWrapper<ViolationAppeal>()
                .eq(ViolationAppeal::getId, appealId)
                .eq(ViolationAppeal::getStatus, AppealStatus.PENDING)
                .set(ViolationAppeal::getStatus, AppealStatus.APPROVED)
                .set(ViolationAppeal::getHandlerId, adminId)
                .set(ViolationAppeal::getHandleNote, trimToNull(dto.handleNote()))
                .set(ViolationAppeal::getHandledAt, handledAt)) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "申诉已被其他管理员处理");
        }

        ViolationReport report = requireReport(appeal.getReportId());
        if (reportMapper.update(null, new LambdaUpdateWrapper<ViolationReport>()
                .eq(ViolationReport::getId, report.getId())
                .eq(ViolationReport::getStatus, ViolationStatus.CONFIRMED)
                .set(ViolationReport::getStatus, ViolationStatus.OVERTURNED)) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "原违规记录状态已变化");
        }
        penaltyService.revokePenalty(report.getId());

        long newerConfirmed = reportMapper.selectCount(new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getItemId, report.getItemId())
                .eq(ViolationReport::getStatus, ViolationStatus.CONFIRMED)
                .gt(ViolationReport::getId, report.getId()));
        Item item = itemMapper.selectById(report.getItemId());
        if (item != null && newerConfirmed == 0 && item.getModerationStatus() == ModerationStatus.REJECTED) {
            item.setModerationStatus(ModerationStatus.PASSED);
            if (item.getStatus() == ItemStatus.OFF_SHELF) item.setStatus(ItemStatus.ON_SALE);
            itemMapper.updateById(item);
            tagQueryService.invalidate(item.getSchoolId());
        }
    }

    @Transactional
    public void reject(Long appealId, Long adminId, HandleAppealDTO dto) {
        requirePendingAppeal(appealId);
        if (appealMapper.update(null, new LambdaUpdateWrapper<ViolationAppeal>()
                .eq(ViolationAppeal::getId, appealId)
                .eq(ViolationAppeal::getStatus, AppealStatus.PENDING)
                .set(ViolationAppeal::getStatus, AppealStatus.REJECTED)
                .set(ViolationAppeal::getHandlerId, adminId)
                .set(ViolationAppeal::getHandleNote, trimToNull(dto.handleNote()))
                .set(ViolationAppeal::getHandledAt, LocalDateTime.now())) == 0) {
            throw new BusinessException(ResultCode.CONFLICT, "申诉已被其他管理员处理");
        }
    }

    private List<AppealVO> assemble(List<ViolationAppeal> appeals, Map<Long, ViolationReport> reports) {
        if (appeals.isEmpty()) return List.of();
        Set<Long> itemIds = new HashSet<>();
        Set<Long> userIds = new HashSet<>();
        for (ViolationAppeal appeal : appeals) {
            if (appeal.getItemId() != null) itemIds.add(appeal.getItemId());
            if (appeal.getUserId() != null) userIds.add(appeal.getUserId());
            if (appeal.getHandlerId() != null) userIds.add(appeal.getHandlerId());
        }
        Map<Long, Item> items = itemIds.isEmpty() ? Map.of()
                : itemMapper.selectByIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        Map<Long, SysUser> users = userIds.isEmpty() ? Map.of()
                : userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        return appeals.stream().map(appeal -> {
            ViolationReport report = reports.get(appeal.getReportId());
            Item item = items.get(appeal.getItemId());
            SysUser seller = users.get(appeal.getUserId());
            SysUser handler = users.get(appeal.getHandlerId());
            return new AppealVO(appeal.getId(), appeal.getReportId(), appeal.getItemId(), appeal.getUserId(),
                    seller == null ? "未知用户" : seller.getNickname(),
                    item == null ? (report == null ? "未知商品" : report.getOriginalTitle()) : item.getTitle(),
                    report == null ? "" : report.getViolationReason(), appeal.getReason(), appeal.getStatus().code(),
                    appeal.getHandlerId(), handler == null ? null : handler.getNickname(), appeal.getHandleNote(),
                    appeal.getCreatedAt(), appeal.getHandledAt());
        }).toList();
    }

    private ViolationAppeal requirePendingAppeal(Long id) {
        ViolationAppeal appeal = appealMapper.selectById(id);
        if (appeal == null) throw new BusinessException(ResultCode.NOT_FOUND, "申诉记录不存在");
        if (appeal.getStatus() != AppealStatus.PENDING) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "该申诉已处理");
        }
        return appeal;
    }

    private ViolationReport requireReport(Long id) {
        ViolationReport report = reportMapper.selectById(id);
        if (report == null || report.getItemId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "违规记录不存在");
        }
        return report;
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
