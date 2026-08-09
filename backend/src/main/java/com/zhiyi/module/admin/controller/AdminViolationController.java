package com.zhiyi.module.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.dto.ConfirmViolationDTO;
import com.zhiyi.module.admin.dto.HandleAppealDTO;
import com.zhiyi.module.admin.service.AdminViolationService;
import com.zhiyi.module.admin.service.ViolationAppealService;
import com.zhiyi.module.admin.vo.AppealVO;
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
@RoleRequired
public class AdminViolationController {

    private final AdminViolationService violationService;
    private final ViolationAppealService appealService;

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
     * 确认违规：下架商品并执行固定警告扣分。
     */
    @PutMapping("/violations/{id}/confirm")
    public Result<?> confirm(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmViolationDTO dto,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        violationService.confirmViolation(id, dto, adminId);
        return Result.ok("违规已确认，商品已下架并扣除合规分");
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

    @GetMapping("/appeals")
    public Result<IPage<AppealVO>> appeals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return Result.ok(appealService.list(page, size, status));
    }

    @PutMapping("/appeals/{id}/approve")
    public Result<Void> approveAppeal(@PathVariable Long id,
                                      @Valid @RequestBody HandleAppealDTO dto,
                                      HttpServletRequest request) {
        appealService.approve(id, (Long) request.getAttribute("userId"), dto);
        return Result.ok("申诉已通过，处罚已撤销", null);
    }

    @PutMapping("/appeals/{id}/reject")
    public Result<Void> rejectAppeal(@PathVariable Long id,
                                     @Valid @RequestBody HandleAppealDTO dto,
                                     HttpServletRequest request) {
        appealService.reject(id, (Long) request.getAttribute("userId"), dto);
        return Result.ok("申诉已驳回", null);
    }
}
