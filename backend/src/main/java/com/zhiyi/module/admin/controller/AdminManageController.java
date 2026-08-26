package com.zhiyi.module.admin.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.service.AdminLineageService;
import com.zhiyi.module.admin.service.AdminManageService;
import com.zhiyi.module.admin.vo.ItemLineageVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 超管控制台 · 账号与内容管理（4.7）
 *
 * POST /api/admin/reset-password          强制重置用户密码
 * GET  /api/admin/item/{id}/lineage       商品传承链
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RoleRequired
public class AdminManageController {

    private final AdminManageService manageService;
    private final AdminLineageService lineageService;

    /**
     * 强制重置密码
     */
    @PostMapping("/reset-password")
    @BusinessErrors({ResultCode.USER_NOT_FOUND, ResultCode.FORBIDDEN})
    public ApiSuccess<Void> resetPassword(
            @Validated @RequestBody ResetPasswordRequest body,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        manageService.resetPassword(body.userId(), adminId);
        return ApiSuccess.ok("密码已重置为 123456", null);
    }

    /**
     * 商品传承链（D3）
     */
    @GetMapping("/item/{id}/lineage")
    @BusinessErrors(ResultCode.NOT_FOUND)
    public ApiSuccess<ItemLineageVO> lineage(@PathVariable Long id,
                                         @RequestParam(required = false) Long schoolId) {
        return ApiSuccess.ok(lineageService.getLineage(id, schoolId));
    }

    /**
     * 标签建议（管理端）：按专题名称生成候选标签，供专题配置选择，不落库。
     * 管理员账号被 RoleInterceptor 限制在 /api/admin/**，故此处提供独立入口。
     */
    @PostMapping("/item/tag-suggestions")
    @BusinessErrors
    public ApiSuccess<java.util.List<String>> tagSuggestions(
            @Validated @RequestBody TagSuggestionRequest request) {
        return ApiSuccess.ok(manageService.suggestTags(request.title(), request.categoryId()));
    }

    /**
     * 内部 DTO
     */
    record TagSuggestionRequest(
            @NotNull(message = "标题不能为空") String title,
            Long categoryId) {
    }

    record ResetPasswordRequest(@NotNull(message = "用户ID不能为空") Long userId) {}
}
