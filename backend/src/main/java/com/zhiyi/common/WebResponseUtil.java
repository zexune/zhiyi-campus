package com.zhiyi.common;

import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在拦截器等无法直接返回 {@link Result} 的位置写入统一 JSON 响应。
 */
public final class WebResponseUtil {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder().build();

    private WebResponseUtil() {
    }

    public static void writeJson(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(JSON_MAPPER.writeValueAsString(Result.fail(code, message)));
    }
}
