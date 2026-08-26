package com.zhiyi.common;

import com.zhiyi.common.ResultCode.RequestOutcome;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * 业务异常，抛出后被 GlobalExceptionHandler 统一处理。
 * HTTP 状态码由 {@link ResultCode} 的映射表决定，业务码保持不变；
 * 只接受已登记的 ResultCode（P1-2：未知码无静默兜底，登记检查在构建期失败）。
 *
 * Retry-After 与 requestOutcome 均支持实例级覆盖：前者用于登录锁定等动态退避，
 * 后者用于同一业务码存在不同执行事实的场景（例如 TRADE_BUSY 的单飞碰撞与容量拒绝）。
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ResultCode resultCode;
    private final int code;
    private final HttpStatus httpStatus;

    /** 可选的冲突详情（如资料乐观并发 409 时携带服务端最新资料，前端展示并要求确认合并）。 */
    private transient Object conflictDetail;

    /** 实例级 Retry-After 覆盖（秒）；null 表示沿用 ResultCode 静态默认策略。 */
    private transient Integer retryAfterSeconds;

    /** 实例级请求结果覆盖；null 表示沿用 ResultCode 的保守默认分类。 */
    private transient RequestOutcome requestOutcomeOverride;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    public BusinessException withConflictDetail(Object detail) {
        this.conflictDetail = detail;
        return this;
    }

    public BusinessException withRetryAfterSeconds(int seconds) {
        // clamp 而非拒绝：非正值视为 1 秒（登录剩余秒数在竞态下可能极小，但仍应给出可退避信号）
        this.retryAfterSeconds = Math.max(1, seconds);
        return this;
    }

    public BusinessException withRequestOutcome(RequestOutcome requestOutcome) {
        this.requestOutcomeOverride = Objects.requireNonNull(requestOutcome, "requestOutcome");
        return this;
    }

    /** 最终生效的退避秒数：实例覆盖值（已 clamp）优先，否则取 ResultCode 静态默认（0 表示不返回头）。 */
    public int effectiveRetryAfterSeconds() {
        return retryAfterSeconds != null ? retryAfterSeconds : resultCode.getRetryAfterSeconds();
    }

    /** 最终生效的请求结果：实例事实优先，否则使用业务码的保守默认分类。 */
    public RequestOutcome effectiveRequestOutcome() {
        return requestOutcomeOverride != null ? requestOutcomeOverride : resultCode.requestOutcome();
    }
}
