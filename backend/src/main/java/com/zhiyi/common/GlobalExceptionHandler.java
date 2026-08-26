package com.zhiyi.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局异常处理器
 *
 * 契约约定（双层）：HTTP 状态码负责粗分类（400 参数 / 403 权限与账户拒绝 /
 * 404 不存在 / 409 状态冲突 / 422 内容待审 / 429 背压限流 / 500 系统），
 * 响应体为统一失败信封 {code,message,data,meta}，code 承载细粒度业务码
 * （映射见 ResultCode）。鉴权类 401 由拦截器直写真实状态码并清除 Cookie；
 * 兜底规则：无论异常从哪里产生，只要最终 HTTP 状态为 401 都清除 Cookie。
 *
 * P1-3：失败信封携带 meta.requestOutcome（明确拒绝/处理中/结果不明），
 * 允许退避的失败附标准 Retry-After 头（实例覆盖值 → ResultCode 静态默认）。
 */
@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final AuthTokenCookieWriter cookieWriter;
    private final BusinessErrorContractVerifier contractVerifier;

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiFailure> handleBusinessException(BusinessException e,
                                                              HttpServletRequest request,
                                                              HttpServletResponse response) {
        contractVerifier.verify(request, e);
        log.warn("业务异常：code={} httpStatus={} {}", e.getCode(), e.getHttpStatus(), e.getMessage());
        return failure(response, e.getResultCode(), e.getMessage(), e.getConflictDetail(),
                e.effectiveRetryAfterSeconds(), e.effectiveRequestOutcome());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiFailure> handleValidation(MethodArgumentNotValidException e,
                                                       HttpServletResponse response) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("参数校验失败");
        return of(response, ResultCode.BAD_REQUEST, msg);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiFailure> handleMessageNotReadable(HttpMessageNotReadableException e,
                                                               HttpServletResponse response) {
        log.warn("请求体不可读：{}", e.getMessage());
        return of(response, ResultCode.BAD_REQUEST, "请求体缺失或 JSON 格式错误");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiFailure> handleMissingParameter(MissingServletRequestParameterException e,
                                                             HttpServletResponse response) {
        log.warn("缺少请求参数：{}", e.getParameterName());
        return of(response, ResultCode.BAD_REQUEST, "缺少必填参数: " + e.getParameterName());
    }

    /** 缺少必填 multipart part（如上传接口的 file）→ 400，不得落入兜底 500。 */
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiFailure> handleMissingPart(MissingServletRequestPartException e,
                                                        HttpServletResponse response) {
        log.warn("缺少请求部分：{}", e.getRequestPartName());
        return of(response, ResultCode.BAD_REQUEST, "缺少必填文件: " + e.getRequestPartName());
    }

    /** 请求媒体类型与 endpoint 的 consumes 不匹配 → 保留真实 415。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiFailure> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException e,
                                                                 HttpServletResponse response) {
        log.warn("请求媒体类型不支持：{}", e.getContentType());
        return of(response, ResultCode.UNSUPPORTED_MEDIA_TYPE,
                "请求体格式不支持，请使用 endpoint 声明的 Content-Type");
    }

    /** Accept 与 endpoint 可生成的响应类型不匹配 → 保留真实 406。 */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiFailure> handleNotAcceptable(HttpMediaTypeNotAcceptableException e,
                                                           HttpServletResponse response) {
        log.warn("无法生成客户端可接受的响应格式：{}", e.getMessage());
        return of(response, ResultCode.NOT_ACCEPTABLE, ResultCode.NOT_ACCEPTABLE.getMessage());
    }

    /** 路径存在但 HTTP 方法不受支持 → 保留真实 405。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiFailure> handleMethodNotAllowed(HttpRequestMethodNotSupportedException e,
                                                             HttpServletResponse response) {
        log.warn("请求方法不支持：{}", e.getMethod());
        ResponseEntity.BodyBuilder builder = failureBuilder(
                response, ResultCode.METHOD_NOT_ALLOWED, ResultCode.METHOD_NOT_ALLOWED.getRetryAfterSeconds());
        Set<HttpMethod> supportedMethods = e.getSupportedHttpMethods();
        if (supportedMethods != null && !supportedMethods.isEmpty()) {
            builder.allow(supportedMethods.toArray(HttpMethod[]::new));
        }
        return builder.body(ApiFailure.of(
                ResultCode.METHOD_NOT_ALLOWED, ResultCode.METHOD_NOT_ALLOWED.getMessage(), null));
    }

    /** Servlet multipart 限额拒绝发生在业务层之前 → 保留真实 413。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiFailure> handleUploadTooLarge(MaxUploadSizeExceededException e,
                                                           HttpServletResponse response) {
        log.warn("上传请求超过大小限制：{}", e.getMessage());
        return of(response, ResultCode.PAYLOAD_TOO_LARGE, "上传文件或请求体超过大小限制");
    }

    /** 已是 multipart 但边界、part 编码等结构畸形 → 400。 */
    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ApiFailure> handleMultipart(MultipartException e,
                                                      HttpServletResponse response) {
        log.warn("multipart 请求非法：{}", e.getMessage());
        return of(response, ResultCode.BAD_REQUEST, "文件上传请求格式错误，请使用 multipart/form-data");
    }

    /** 缺少必填请求头（如资金操作幂等键）→ 400，引导客户端补齐后重试。 */
    @ExceptionHandler(org.springframework.web.bind.MissingRequestHeaderException.class)
    public ResponseEntity<ApiFailure> handleMissingHeader(org.springframework.web.bind.MissingRequestHeaderException e,
                                                          HttpServletResponse response) {
        log.warn("缺少请求头：{}", e.getHeaderName());
        return of(response, ResultCode.BAD_REQUEST, "缺少必填请求头: " + e.getHeaderName());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiFailure> handleTypeMismatch(MethodArgumentTypeMismatchException e,
                                                         HttpServletResponse response) {
        String expected = e.getRequiredType() == null ? "未知" : e.getRequiredType().getSimpleName();
        log.warn("参数类型不匹配：{} 需要 {}", e.getName(), expected);
        return of(response, ResultCode.BAD_REQUEST,
                "参数 " + e.getName() + " 类型错误，应为 " + expected);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiFailure> handleConstraintViolation(ConstraintViolationException e,
                                                                HttpServletResponse response) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        return of(response, ResultCode.BAD_REQUEST, msg.isEmpty() ? "参数校验失败" : msg);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiFailure> handleNoResource(NoResourceFoundException e,
                                                       HttpServletResponse response) {
        log.warn("资源不存在：{}", e.getResourcePath());
        return of(response, ResultCode.NOT_FOUND, "请求的资源不存在");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiFailure> handleException(Exception e, HttpServletResponse response) {
        log.error("系统异常", e);
        return of(response, ResultCode.SERVER_ERROR, ResultCode.SERVER_ERROR.getMessage());
    }

    private ResponseEntity<ApiFailure> of(HttpServletResponse response, ResultCode code, String message) {
        return failure(response, code, message, null, code.getRetryAfterSeconds());
    }

    /**
     * 统一收口：Retry-After 只在确有退避建议时返回；
     * 最终 HTTP 状态为 401 时无论异常来源一律清除会话 Cookie。
     */
    private ResponseEntity<ApiFailure> failure(HttpServletResponse response, ResultCode code, String message,
                                               Object detail, int retryAfterSeconds) {
        return failure(response, code, message, detail, retryAfterSeconds, code.requestOutcome());
    }

    private ResponseEntity<ApiFailure> failure(HttpServletResponse response, ResultCode code, String message,
                                               Object detail, int retryAfterSeconds,
                                               ResultCode.RequestOutcome requestOutcome) {
        return failureBuilder(response, code, retryAfterSeconds)
                .body(ApiFailure.of(code, message, detail, requestOutcome));
    }

    /**
     * 所有失败都显式选择 JSON：包括客户端 Accept 无法满足时的 406，仍返回可解析的
     * ApiFailure，而不是在异常处理器内再次触发内容协商并退化为空响应。
     */
    private ResponseEntity.BodyBuilder failureBuilder(HttpServletResponse response, ResultCode code,
                                                       int retryAfterSeconds) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(code.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON);
        if (retryAfterSeconds > 0) {
            builder.header(HttpHeaders.RETRY_AFTER, Integer.toString(retryAfterSeconds));
        }
        if (code.getHttpStatus().value() == 401) {
            cookieWriter.clear(response);
        }
        return builder;
    }
}
