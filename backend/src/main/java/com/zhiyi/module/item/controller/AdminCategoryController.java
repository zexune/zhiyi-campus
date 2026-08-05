package com.zhiyi.module.item.controller;

import com.zhiyi.common.Result;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.module.item.dto.CategoryDTO;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.service.CategoryAdminService;
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
@RoleRequired("ADMIN")
public class AdminCategoryController {

    private final CategoryAdminService categoryAdminService;

    @GetMapping
    public Result<List<Category>> list() {
        return Result.ok(categoryAdminService.list());
    }

    @PostMapping
    public Result<Category> create(@Valid @RequestBody CategoryDTO dto) {
        return Result.ok("分类创建成功", categoryAdminService.create(dto));
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @Valid @RequestBody CategoryDTO dto) {
        return Result.ok("分类修改成功", categoryAdminService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryAdminService.delete(id);
        return Result.ok("分类删除成功", null);
    }
}
