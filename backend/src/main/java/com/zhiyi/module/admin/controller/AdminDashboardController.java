package com.zhiyi.module.admin.controller;

import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.service.AdminDashboardService;
import com.zhiyi.module.admin.vo.AdminDashboardVO;
import com.zhiyi.module.admin.vo.TradeHeatmapVO;
import lombok.RequiredArgsConstructor;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 超管控制台 · 数据大盘
 *
 * GET /api/admin/dashboard    数据概览（统计卡片 + 最近违规）
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RoleRequired("ADMIN")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping("/dashboard")
    public Result<AdminDashboardVO> dashboard(
            @RequestParam(required = false) Long schoolId) {
        return Result.ok(dashboardService.getDashboard(schoolId));
    }

    /**
     * 交易热力图（D5）：统计各 trade_location 的交易频次
     */
    @GetMapping("/trade-heatmap")
    public Result<List<TradeHeatmapVO>> tradeHeatmap(
            @RequestParam(required = false) Long schoolId) {
        return Result.ok(dashboardService.getTradeHeatmap(schoolId));
    }
}
