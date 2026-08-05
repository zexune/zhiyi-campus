package com.zhiyi.module.user.controller;

import com.zhiyi.common.Result;
import com.zhiyi.module.user.service.SchoolService;
import com.zhiyi.module.user.vo.SchoolVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模块一创新功能：学校列表（A9）
 *
 * GET /api/school/list  全部 ACTIVE 学校（公开，注册页下拉用）
 */
@RestController
@RequestMapping("/api/school")
@RequiredArgsConstructor
public class SchoolController {

    private final SchoolService schoolService;

    @GetMapping("/list")
    public Result<List<SchoolVO>> list() {
        return Result.ok(schoolService.listActiveSchools());
    }
}
