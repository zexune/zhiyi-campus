package com.zhiyi.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 对外分页契约（P1-6）：公开边界统一返回本类型，MyBatis-Plus {@link IPage}
 * 只在服务内部使用，不泄漏到 Controller / OpenAPI。
 *
 * 首版字段与旧 IPage 序列化保持一致（current/size/pages/records/total），
 * 前端在 adapters 层归一化为 PageResult（只读 records/total）。
 * 删除或改名任何兼容键都需要 ADR、消费者盘点与显式批准。
 */
@Data
public class PageResponse<T> {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前页码（从 1 开始）")
    private long current;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "每页条数")
    private long size;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "总页数")
    private long pages;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "总记录数")
    private long total;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前页数据")
    private List<T> records;

    public static <T> PageResponse<T> from(IPage<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.setCurrent(page.getCurrent());
        response.setSize(page.getSize());
        response.setPages(page.getPages());
        response.setTotal(page.getTotal());
        response.setRecords(page.getRecords());
        return response;
    }
}
