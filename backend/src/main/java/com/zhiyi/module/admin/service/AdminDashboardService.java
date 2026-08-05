package com.zhiyi.module.admin.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zhiyi.module.admin.entity.ViolationReport;
import com.zhiyi.module.admin.mapper.ViolationReportMapper;
import com.zhiyi.module.admin.vo.AdminDashboardVO;
import com.zhiyi.module.admin.vo.TradeHeatmapVO;
import com.zhiyi.module.item.entity.Item;
import com.zhiyi.module.item.mapper.ItemMapper;
import com.zhiyi.module.trade.entity.TradeOrder;
import com.zhiyi.module.trade.mapper.TradeOrderMapper;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 超管数据大盘服务（D2：支持多校切换）
 *
 * schoolId 为 null → 全局视角（不隔离）
 * schoolId 非空   → 仅统计该校数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final SysUserMapper sysUserMapper;
    private final ItemMapper itemMapper;
    private final TradeOrderMapper orderMapper;
    private final ViolationReportMapper violationReportMapper;

    /**
     * 聚合大盘统计数据 + 近 7 日趋势 + 最近 5 条待审核违规
     *
     * @param schoolId 可选，指定学校视角；null = 全局
     */
    public AdminDashboardVO getDashboard(Long schoolId) {
        AdminDashboardVO vo = new AdminDashboardVO();

        // 1. 用户总数（仅普通用户，排除管理员）
        LambdaQueryWrapper<SysUser> userQ = new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRole, "USER");
        if (schoolId != null) {
            userQ.eq(SysUser::getSchoolId, schoolId);
        }
        vo.setTotalUsers(sysUserMapper.selectCount(userQ));

        // 2. 在售商品数
        LambdaQueryWrapper<Item> itemQ = new LambdaQueryWrapper<Item>()
                .eq(Item::getStatus, "ON_SALE")
                .eq(Item::getIsDeleted, false);
        if (schoolId != null) {
            itemQ.eq(Item::getSchoolId, schoolId);
        }
        vo.setOnSaleItems(itemMapper.selectCount(itemQ));

        // 3. 今日交易总额 —— 需关联 item 做学校过滤
        LambdaQueryWrapper<TradeOrder> todayQ = new LambdaQueryWrapper<TradeOrder>()
                .apply("status = 'COMPLETED' AND DATE(completed_at) = CURDATE()");
        if (schoolId != null) {
            todayQ.apply("item_id IN (SELECT id FROM item WHERE school_id = {0})", schoolId);
        }
        List<TradeOrder> todayOrders = orderMapper.selectList(todayQ);
        BigDecimal todaySum = todayOrders.stream()
                .map(TradeOrder::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        vo.setTodayTradeAmount(todaySum.setScale(2, RoundingMode.HALF_UP).toString());
        log.info("今日交易额：schoolId={}, 订单数={}, 总额={}", schoolId, todayOrders.size(), vo.getTodayTradeAmount());

        // 4. 待审核违规数 —— 需关联 sys_user 做学校过滤
        LambdaQueryWrapper<ViolationReport> pendingQ = new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getStatus, "PENDING");
        if (schoolId != null) {
            pendingQ.apply("user_id IN (SELECT id FROM sys_user WHERE school_id = {0})", schoolId);
        }
        vo.setPendingViolations(violationReportMapper.selectCount(pendingQ));

        // 5. 最近 5 条待审核违规
        LambdaQueryWrapper<ViolationReport> recentQ = new LambdaQueryWrapper<ViolationReport>()
                .eq(ViolationReport::getStatus, "PENDING")
                .orderByDesc(ViolationReport::getCreatedAt)
                .last("LIMIT 5");
        if (schoolId != null) {
            recentQ.apply("user_id IN (SELECT id FROM sys_user WHERE school_id = {0})", schoolId);
        }

        List<ViolationReport> recent = violationReportMapper.selectList(recentQ);
        List<AdminDashboardVO.RecentViolation> rvList = new ArrayList<>();

        if (!recent.isEmpty()) {
            List<Long> userIds = recent.stream()
                    .map(ViolationReport::getUserId)
                    .distinct()
                    .collect(Collectors.toList());
            List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
            Map<Long, String> nickMap = users.stream()
                    .collect(Collectors.toMap(SysUser::getId, SysUser::getNickname, (a, b) -> a));

            for (ViolationReport r : recent) {
                AdminDashboardVO.RecentViolation rv = new AdminDashboardVO.RecentViolation();
                rv.setId(r.getId());
                rv.setUserId(r.getUserId());
                rv.setReporterName(nickMap.getOrDefault(r.getUserId(), "未知用户"));
                rv.setOriginalTitle(r.getOriginalTitle());
                rv.setViolationType(r.getViolationType());
                rv.setViolationReason(r.getViolationReason());
                rv.setCreatedAt(r.getCreatedAt());
                rvList.add(rv);
            }
        }
        vo.setRecentViolations(rvList);

        // 6. 近 7 日交易趋势
        vo.setTrend(computeTrend(schoolId));

        return vo;
    }

    /**
     * 计算近 7 日交易趋势，支持学校过滤
     */
    private List<AdminDashboardVO.TradeTrendPoint> computeTrend(Long schoolId) {
        LambdaQueryWrapper<TradeOrder> q = new LambdaQueryWrapper<TradeOrder>()
                .apply("status = 'COMPLETED' AND completed_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)");
        if (schoolId != null) {
            q.apply("item_id IN (SELECT id FROM item WHERE school_id = {0})", schoolId);
        }

        List<TradeOrder> completedOrders = orderMapper.selectList(q);

        Map<LocalDate, Long> dayCount = new java.util.LinkedHashMap<>();
        Map<LocalDate, BigDecimal> dayAmount = new java.util.LinkedHashMap<>();
        for (TradeOrder o : completedOrders) {
            if (o.getCompletedAt() == null) continue;
            LocalDate d = o.getCompletedAt().toLocalDate();
            dayCount.merge(d, 1L, Long::sum);
            dayAmount.merge(d, o.getPrice(), BigDecimal::add);
        }

        LocalDate today = LocalDate.now();
        LocalDate start = today.minusDays(6);
        List<AdminDashboardVO.TradeTrendPoint> points = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            AdminDashboardVO.TradeTrendPoint p = new AdminDashboardVO.TradeTrendPoint();
            p.setDate(d.toString());
            p.setCount(dayCount.getOrDefault(d, 0L));
            p.setTotalAmount(dayAmount.getOrDefault(d, BigDecimal.ZERO)
                    .setScale(2, RoundingMode.HALF_UP).toString());
            points.add(p);
        }

        log.info("近7日趋势：schoolId={}, 完成订单数={}, 趋势点={}", schoolId, completedOrders.size(), points.size());
        return points;
    }

    /**
     * 交易热力图（D5）：统计各 trade_location 的 COMPLETED 订单频次
     *
     * 联表统计：trade_order（COMPLETED）JOIN item → 按 item.trade_location 分组计数
     *
     * @param schoolId 可选，null = 全局
     */
    public List<TradeHeatmapVO> getTradeHeatmap(Long schoolId) {
        // 1. 查询所有 COMPLETED 订单
        List<TradeOrder> completedOrders = orderMapper.selectList(
                new LambdaQueryWrapper<TradeOrder>()
                        .eq(TradeOrder::getStatus, "COMPLETED"));
        if (completedOrders.isEmpty()) {
            return java.util.List.of();
        }

        // 2. 提取 itemId 并批量查商品
        List<Long> itemIds = completedOrders.stream()
                .map(TradeOrder::getItemId)
                .distinct()
                .collect(Collectors.toList());

        LambdaQueryWrapper<Item> itemQ = new LambdaQueryWrapper<Item>()
                .in(Item::getId, itemIds)
                .isNotNull(Item::getTradeLocation)
                .ne(Item::getTradeLocation, "");
        if (schoolId != null) {
            itemQ.eq(Item::getSchoolId, schoolId);
        }
        Map<Long, String> locationMap = itemMapper.selectList(itemQ).stream()
                .collect(Collectors.toMap(Item::getId, Item::getTradeLocation, (a, b) -> a));

        // 3. 按 location 统计 COMPLETED 订单数
        Map<String, Long> locationCount = new java.util.LinkedHashMap<>();
        for (TradeOrder o : completedOrders) {
            String loc = locationMap.get(o.getItemId());
            if (loc == null || loc.trim().isEmpty()) continue;
            locationCount.merge(loc.trim(), 1L, Long::sum);
        }

        // 4. 按次数降序排列
        return locationCount.entrySet().stream()
                .map(e -> new TradeHeatmapVO(e.getKey(), e.getValue()))
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .toList();
    }
}
