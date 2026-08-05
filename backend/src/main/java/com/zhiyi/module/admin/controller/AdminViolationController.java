package com.zhiyi.module.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.dto.ConfirmViolationDTO;
import com.zhiyi.module.admin.service.AdminViolationService;
import com.zhiyi.module.admin.vo.PenaltyStatsVO;
import com.zhiyi.module.admin.vo.ViolationVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 超管控制台 · 违规审核工作台（4.5）
 *
 * GET    /api/admin/violations              违规记录列表
 * PUT    /api/admin/violations/{id}/confirm  确认违规 + 处罚
 * PUT    /api/admin/violations/{id}/dismiss  误判放行
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RoleRequired("ADMIN")
public class AdminViolationController {

    private final AdminViolationService violationService;

    /**
     * 违规记录列表（支持按状态筛选）
     */
    @GetMapping("/violations")
    public Result<IPage<ViolationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return Result.ok(violationService.getViolations(page, size, status));
    }

    /**
     * 确认违规 + 处罚用户
     */
    @PutMapping("/violations/{id}/confirm")
    public Result<?> confirm(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmViolationDTO dto,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        violationService.confirmViolation(id, dto, adminId);
        return Result.ok("违规已确认，处罚已生效");
    }

    /**
     * 用户处罚评分统计（D4：独立信誉处罚）
     */
    @GetMapping("/penalty-stats")
    public Result<PenaltyStatsVO> penaltyStats(@RequestParam Long userId) {
        return Result.ok(violationService.getPenaltyStats(userId));
    }

    /**
     * 误判放行
     */
    @PutMapping("/violations/{id}/dismiss")
    public Result<?> dismiss(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        violationService.dismissViolation(id, adminId);
        return Result.ok("已放行，该违规记录已撤销");
    }
}
