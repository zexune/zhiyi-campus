package com.zhiyi.common;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 契约约定：控制器层错误统一返回 HTTP 200 + body code（与 ResultCode 对应），
 * 鉴权类错误（401/403）由拦截器直接写真实 HTTP 状态码。
 * 所有客户端输入错误都必须映射到明确的 4xx code，禁止落入兜底 handler 变成 500。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={} {}", e.getCode(), e.getMessage());
        return e.getConflictDetail() == null
                ? Result.fail(e.getCode(), e.getMessage())
                : Result.fail(e.getCode(), e.getMessage(), e.getConflictDetail());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return Result.fail(ResultCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<?> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体不可读：{}", e.getMessage());
        return Result.fail(ResultCode.BAD_REQUEST, "请求体缺失或 JSON 格式错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<?> handleMissingParameter(MissingServletRequestParameterException e) {
        log.warn("缺少请求参数：{}", e.getParameterName());
        return Result.fail(ResultCode.BAD_REQUEST, "缺少必填参数: " + e.getParameterName());
    }

    /** 缺少必填请求头（如资金操作幂等键）→ 400，引导客户端补齐后重试。 */
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public Result<?> handleMissingHeader(org.springframework.web.bind.MissingRequestHeaderException e) {
        log.warn("缺少请求头：{}", e.getHeaderName());
        return Result.fail(ResultCode.BAD_REQUEST, "缺少必填请求头: " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<?> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String expected = e.getRequiredType() == null ? "未知" : e.getRequiredType().getSimpleName();
        log.warn("参数类型不匹配：{} 需要 {}", e.getName(), expected);
        return Result.fail(ResultCode.BAD_REQUEST,
                "参数 " + e.getName() + " 类型错误，应为 " + expected);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return Result.fail(ResultCode.BAD_REQUEST, msg.isEmpty() ? "参数校验失败" : msg);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<?> handleNoResource(NoResourceFoundException e) {
        log.warn("资源不存在：{}", e.getResourcePath());
        return Result.fail(ResultCode.NOT_FOUND, "请求的资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.fail(ResultCode.SERVER_ERROR);
    }
}
