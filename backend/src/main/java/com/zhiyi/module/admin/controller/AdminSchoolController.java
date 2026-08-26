package com.zhiyi.module.admin.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.admin.dto.SchoolDTO;
import com.zhiyi.module.admin.service.AdminSchoolService;
import com.zhiyi.module.user.vo.SchoolVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 超管控制台 · 学校管理（D1）
 *
 * GET    /api/admin/schools       全部学校列表
 * POST   /api/admin/schools       新增学校
 * PUT    /api/admin/schools/{id}  编辑学校
 * DELETE /api/admin/schools/{id}  删除学校
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@RoleRequired
public class AdminSchoolController {

    private final AdminSchoolService schoolService;

    @GetMapping("/schools")
    @BusinessErrors
    public ApiSuccess<List<SchoolVO>> list(@RequestParam(required = false) String status) {
        return ApiSuccess.ok(schoolService.listAll(status));
    }

    @PostMapping("/schools")
    @BusinessErrors
    public ApiSuccess<SchoolVO> create(@Valid @RequestBody SchoolDTO dto) {
        return ApiSuccess.ok(schoolService.create(dto));
    }

    @PutMapping("/schools/{id}")
    @BusinessErrors(ResultCode.NOT_FOUND)
    public ApiSuccess<SchoolVO> update(@PathVariable Long id, @Valid @RequestBody SchoolDTO dto) {
        return ApiSuccess.ok(schoolService.update(id, dto));
    }

    @DeleteMapping("/schools/{id}")
    @BusinessErrors(ResultCode.NOT_FOUND)
    public ApiSuccess<Void> delete(@PathVariable Long id) {
        schoolService.delete(id);
        return ApiSuccess.ok("学校已删除", null);
    }
}
