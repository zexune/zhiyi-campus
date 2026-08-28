package com.zhiyi.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

/**
 * 对外分页契约（P1-6）：公开边界统一返回本类型，MyBatis-Plus {@link IPage}
 * 只在服务内部使用，不泄漏到 Controller / OpenAPI。
 *
 * 只暴露前端消费的 total/records；页码与每页大小由客户端查询参数本地维护，
 * 不在响应中回显。
 */
@Data
public class PageResponse<T> {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "总记录数")
    private long total;

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "当前页数据")
    private List<T> records;

    public static <T> PageResponse<T> from(IPage<T> page) {
        PageResponse<T> response = new PageResponse<>();
        response.setTotal(page.getTotal());
        response.setRecords(page.getRecords());
        return response;
    }
}
