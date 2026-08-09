package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.common.enums.ItemStatus;
import com.zhiyi.common.enums.OrderStatus;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.ViolationStatus;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.AdminDashboardVO;
import com.zhiyi.module.admin.vo.TradeHeatmapVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.trade.vo.DailyTradeStatRow;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final SysUserMapper userMapper;
    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final ViolationReportMapper violationReportMapper;

    public AdminDashboardVO getDashboard(Long schoolId) {
        AdminDashboardVO vo = new AdminDashboardVO();
        vo.setTotalUsers(userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, UserRole.USER)
                .eq(schoolId != null, SysUser::getSchoolId, schoolId)));
        vo.setOnSaleItems(itemMapper.selectCount(new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, ItemStatus.ON_SALE)
                .eq(Item::getIsDeleted, false)
                .eq(schoolId != null, Item::getSchoolId, schoolId)));

        LocalDate today = LocalDate.now();
        LocalDateTime todayStart = today.atStartOfDay();
        BigDecimal todayAmount = orderMapper.sumCompletedAmount(
                OrderStatus.COMPLETED, todayStart, todayStart.plusDays(1), schoolId);
        vo.setTodayTradeAmount(money(todayAmount));

        LambdaQueryWrapper<ViolationReport> pending = pendingViolations(schoolId);
        vo.setPendingViolations(violationReportMapper.selectCount(pending));
        List<ViolationReport> recent = violationReportMapper.selectList(
                pendingViolations(schoolId)
                        .orderByDesc(ViolationReport::getCreatedAt)
                        .orderByDesc(ViolationReport::getId)
                        .last("LIMIT 5"));
        vo.setRecentViolations(assembleRecent(recent));
        vo.setTrend(computeTrend(today, schoolId));
        return vo;
    }

    public List<TradeHeatmapVO> getTradeHeatmap(Long schoolId) {
        return orderMapper.selectLocationStats(OrderStatus.COMPLETED, schoolId).stream()
                .map(row -> new TradeHeatmapVO(row.location(), row.tradeCount()))
                .toList();
    }

    private List<AdminDashboardVO.TradeTrendPoint> computeTrend(LocalDate today, Long schoolId) {
        LocalDate startDate = today.minusDays(6);
        Map<LocalDate, DailyTradeStatRow> stats = orderMapper.selectDailyStats(
                        OrderStatus.COMPLETED, startDate.atStartOfDay(), today.plusDays(1).atStartOfDay(), schoolId)
                .stream().collect(Collectors.toMap(DailyTradeStatRow::tradeDate, Function.identity()));
        List<AdminDashboardVO.TradeTrendPoint> points = new ArrayList<>(7);
        for (LocalDate date = startDate; !date.isAfter(today); date = date.plusDays(1)) {
            DailyTradeStatRow stat = stats.get(date);
            AdminDashboardVO.TradeTrendPoint point = new AdminDashboardVO.TradeTrendPoint();
            point.setDate(date.toString());
            point.setCount(stat == null ? 0L : stat.tradeCount());
            point.setTotalAmount(money(stat == null ? BigDecimal.ZERO : stat.totalAmount()));
            points.add(point);
        }
        return points;
    }

    private LambdaQueryWrapper<ViolationReport> pendingViolations(Long schoolId) {
        LambdaQueryWrapper<ViolationReport> query = new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getStatus, ViolationStatus.PENDING);
        if (schoolId != null) {
            query.apply("user_id IN (SELECT u.id FROM sys_user u WHERE u.school_id = {0})", schoolId);
        }
        return query;
    }

    private List<AdminDashboardVO.RecentViolation> assembleRecent(List<ViolationReport> recent) {
        if (recent.isEmpty()) return List.of();
        List<Long> userIds = recent.stream().map(ViolationReport::getUserId).distinct().toList();
        Map<Long, String> names = userMapper.selectByIds(userIds).stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname));
        return recent.stream().map(report -> {
            AdminDashboardVO.RecentViolation item = new AdminDashboardVO.RecentViolation();
            item.setId(report.getId());
            item.setUserId(report.getUserId());
            item.setReporterName(names.getOrDefault(report.getUserId(), "未知用户"));
            item.setOriginalTitle(report.getOriginalTitle());
            item.setViolationType(report.getViolationType());
            item.setViolationReason(report.getViolationReason());
            item.setCreatedAt(report.getCreatedAt());
            return item;
        }).toList();
    }

    private String money(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount)
                .setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
