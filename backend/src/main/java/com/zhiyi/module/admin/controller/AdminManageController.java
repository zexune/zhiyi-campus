package com.zhiyi.module.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.service.AdminLineageService;
import com.zhiyi.module.admin.service.AdminManageService;
import com.zhiyi.module.admin.vo.AdminItemVO;
import com.zhiyi.module.admin.vo.ItemLineageVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 超管控制台 · 内容强制管理（4.7）
 *
 * PUT  /api/admin/item/{id}/force-off-shelf  强制下架商品
 * POST /api/admin/reset-password             强制重置用户密码
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RoleRequired("ADMIN")
public class AdminManageController {

    private final AdminManageService manageService;
    private final AdminLineageService lineageService;

    /**
     * 管理员商品检索（4.7 强制下架前选择商品用）
     */
    @GetMapping("/items")
    public Result<IPage<AdminItemVO>> searchItems(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        return Result.ok(manageService.searchItems(keyword, status, page, size));
    }

    /**
     * 强制下架商品
     */
    @PutMapping("/item/{id}/force-off-shelf")
    public Result<?> forceOffShelf(
            @PathVariable Long id,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        manageService.forceOffShelf(id, adminId);
        return Result.ok("商品已强制下架");
    }

    /**
     * 强制重置密码
     */
    @PostMapping("/reset-password")
    public Result<?> resetPassword(
            @Validated @RequestBody ResetPasswordRequest body,
            HttpServletRequest request) {
        Long adminId = (Long) request.getAttribute("userId");
        manageService.resetPassword(body.userId(), adminId);
        return Result.ok("密码已重置为 123456");
    }

    /**
     * 商品传承链（D3）
     */
    @GetMapping("/item/{id}/lineage")
    public Result<ItemLineageVO> lineage(@PathVariable Long id,
                                         @RequestParam(required = false) Long schoolId) {
        return Result.ok(lineageService.getLineage(id, schoolId));
    }

    /**
     * 内部 DTO
     */
    record ResetPasswordRequest(@NotNull(message = "用户ID不能为空") Long userId) {}
}
