package com.zhiyi.module.item.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.item.dto.CategoryDTO;
import com.zhiyi.module.item.service.CategoryAdminService;
import com.zhiyi.module.item.vo.CategoryResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/categories")
@RequiredArgsConstructor
@RoleRequired
public class AdminCategoryController {

    private final CategoryAdminService categoryAdminService;

    @GetMapping
    @BusinessErrors
    public ApiSuccess<List<CategoryResponse>> list() {
        return ApiSuccess.ok(categoryAdminService.list().stream()
                .map(CategoryResponse::from)
                .toList());
    }

    @PostMapping
    @BusinessErrors(ResultCode.CONFLICT)
    public ApiSuccess<CategoryResponse> create(@Valid @RequestBody CategoryDTO dto) {
        return ApiSuccess.ok("分类创建成功", CategoryResponse.from(categoryAdminService.create(dto)));
    }

    @PutMapping("/{id}")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT})
    public ApiSuccess<CategoryResponse> update(@PathVariable Long id, @Valid @RequestBody CategoryDTO dto) {
        return ApiSuccess.ok("分类修改成功", CategoryResponse.from(categoryAdminService.update(id, dto)));
    }

    @DeleteMapping("/{id}")
    @BusinessErrors({ResultCode.NOT_FOUND, ResultCode.CONFLICT})
    public ApiSuccess<Void> delete(@PathVariable Long id) {
        categoryAdminService.delete(id);
        return ApiSuccess.ok("分类删除成功", null);
    }
}
