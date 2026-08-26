package com.zhiyi.module.item.controller;

import com.zhiyi.common.ApiSuccess;
import com.zhiyi.common.annotation.BusinessErrors;
import com.zhiyi.module.item.service.MarketplaceService;
import com.zhiyi.module.item.vo.CategoryResponse;
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

    /** 公开接口（注册/大厅筛选下拉）；分类查询无特有业务错误。 */
    @GetMapping("/list")
    @BusinessErrors
    public ApiSuccess<List<CategoryResponse>> list() {
        return ApiSuccess.ok(marketplaceService.listCategories().stream()
                .map(CategoryResponse::from)
                .toList());
    }
}
