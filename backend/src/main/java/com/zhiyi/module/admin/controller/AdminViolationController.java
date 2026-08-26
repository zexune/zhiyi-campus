package com.zhiyi.module.admin.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.PageResponse;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
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
    @BusinessErrors
    public ApiSuccess<PageResponse<ViolationVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ApiSuccess.ok(PageResponse.from(violationService.getViolations(page, size, status)));
    }

    /**
     * 确认违规：下架商品并执行固定警告扣分。
     * ORDER_STATUS_ERROR：强制取消在途订单时订单状态已迁移的防御分支。
     */
    @PutMapping("/violations/{id}/confirm")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT, ResultCode.ORDER_STATUS_ERROR})
    public ApiSuccess<Void> confirm(
            @PathVariable Long id,
            @Valid @RequestBody ConfirmViolationDTO dto,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        violationService.confirmViolation(id, dto, adminId);
        return ApiSuccess.ok("违规已确认，商品已下架并扣除合规分", null);
    }

    /**
     * 用户处罚评分统计（D4：独立信誉处罚）
     */
    @GetMapping("/penalty-stats")
    @BusinessErrors(ResultCode.NOT_FOUND)
    public ApiSuccess<PenaltyStatsVO> penaltyStats(@RequestParam Long userId) {
        return ApiSuccess.ok(violationService.getPenaltyStats(userId));
    }

    /**
     * 误判放行
     */
    @PutMapping("/violations/{id}/dismiss")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT})
    public ApiSuccess<Void> dismiss(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        violationService.dismissViolation(id, adminId);
        return ApiSuccess.ok("已放行，该违规记录已撤销", null);
    }

    @GetMapping("/appeals")
    @BusinessErrors
    public ApiSuccess<PageResponse<AppealVO>> appeals(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String status) {
        return ApiSuccess.ok(PageResponse.from(appealService.list(page, size, status)));
    }

    @PutMapping("/appeals/{id}/approve")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT, ResultCode.FORBIDDEN})
    public ApiSuccess<Void> approveAppeal(@PathVariable Long id,
                                          @Valid @RequestBody HandleAppealDTO dto,
                                          HttpServletRequest request) {
        appealService.approve(id, (Long) request.getAttribute("userId"), dto);
        return ApiSuccess.ok("申诉已通过，处罚已撤销", null);
    }

    @PutMapping("/appeals/{id}/reject")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT, ResultCode.FORBIDDEN})
    public ApiSuccess<Void> rejectAppeal(@PathVariable Long id,
                                         @Valid @RequestBody HandleAppealDTO dto,
                                         HttpServletRequest request) {
        appealService.reject(id, (Long) request.getAttribute("userId"), dto);
        return ApiSuccess.ok("申诉已驳回", null);
    }
}
