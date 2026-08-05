package com.zhiyi.module.item.controller;

import com.zhiyi.common.Result;
import com.zhiyi.module.item.entity.Category;
import com.zhiyi.module.item.service.MarketplaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/category")
@RequiredArgsConstructor
public class CategoryController {

    private final MarketplaceService marketplaceService;

    @GetMapping("/list")
    public Result<List<Category>> list() {
        return Result.ok(marketplaceService.listCategories());
    }
}
