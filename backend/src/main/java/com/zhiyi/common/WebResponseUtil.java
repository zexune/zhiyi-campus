package com.zhiyi.common;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 拦截器等无法走 @RestControllerAdvice 的位置的统一失败信封写入器。
 *
 * 作为 Spring Bean 注入应用实际使用的 {@link JsonMapper}：拦截器直写的
 * JSON 与 MVC（GlobalExceptionHandler → HttpMessageConverter）序列化的
 * 信封由同一个 mapper 产生，null/枚举/字段顺序不会出现两套结果。
 * 失败对象一律经 {@link ApiFailure#of} 工厂创建，HTTP 状态与业务码成对出现。
 */
@Component
public class WebResponseUtil {

    private final JsonMapper jsonMapper;

    public WebResponseUtil(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void writeFailure(HttpServletResponse response, ResultCode code, String message)
            throws IOException {
        writeFailure(response, ApiFailure.of(code, message), code.getHttpStatus().value(),
                code.getRetryAfterSeconds());
    }

    /** 业务异常（含实例级 Retry-After/requestOutcome 覆盖与冲突详情）。 */
    public void writeFailure(HttpServletResponse response, BusinessException exception)
            throws IOException {
        writeFailure(response,
                ApiFailure.of(exception.getResultCode(), exception.getMessage(), exception.getConflictDetail(),
                        exception.effectiveRequestOutcome()),
                exception.getHttpStatus().value(),
                exception.effectiveRetryAfterSeconds());
    }

    private void writeFailure(HttpServletResponse response, ApiFailure failure, int httpStatus,
                              int retryAfterSeconds)
            throws IOException {
        response.setStatus(httpStatus);
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        if (retryAfterSeconds > 0) {
            response.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds));
        }
        response.getWriter().write(jsonMapper.writeValueAsString(failure));
    }
}
