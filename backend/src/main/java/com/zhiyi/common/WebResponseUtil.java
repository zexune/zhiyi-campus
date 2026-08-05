package com.zhiyi.common;

import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 在拦截器等无法返回 Result 的地方，手写统一格式的 JSON 响应
 */
public final class WebResponseUtil {

    private WebResponseUtil() {
    }

    public static void writeJson(HttpServletResponse response, int httpStatus, int code, String message)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        // message 只包含我们自己写的固定文案，无注入风险
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + message + "\",\"data\":null}");
    }
}
