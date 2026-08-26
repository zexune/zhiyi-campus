package com.zhiyi.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.zhiyi.common.ResultCode.RequestOutcome;

/**
 * 失败信封（P1-1 类型分离）：wire 形状为
 * {@code {"code":3001,"message":"余额不足","data":null,"meta":{"requestOutcome":"REJECTED"}}}。
 *
 * 不变量（构造期强校验）：
 * - code/message/data/meta 四个字段始终存在，data 无详情时显式为 null（禁止省略）；
 * - meta 与 meta.requestOutcome 不得为 null（前端以 requestOutcome 为幂等处置的权威）；
 * - 失败信封的 code 不可能是 200。
 * 只能通过 {@link #of} 工厂或满足上述不变量的构造器创建。
 */
public record ApiFailure(
        int code,
        String message,
        @JsonInclude(JsonInclude.Include.ALWAYS) Object data,
        FailureMeta meta) {

    public ApiFailure {
        if (code == ResultCode.SUCCESS.getCode()) {
            throw new IllegalArgumentException("失败信封的 code 不能是 200");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("失败信封的 message 不能为空");
        }
        if (meta == null || meta.requestOutcome() == null) {
            throw new IllegalArgumentException("失败信封必须携带 meta.requestOutcome");
        }
    }

    public record FailureMeta(RequestOutcome requestOutcome) {

        public FailureMeta {
            if (requestOutcome == null) {
                throw new IllegalArgumentException("meta.requestOutcome 不能为 null");
            }
        }
    }

    public static ApiFailure of(ResultCode resultCode) {
        return of(resultCode, resultCode.getMessage(), null);
    }

    public static ApiFailure of(ResultCode resultCode, String message) {
        return of(resultCode, message, null);
    }

    /** 冲突详情（如资料 409 时的服务端最新资料）放在 data；无详情时 data 为显式 null。 */
    public static ApiFailure of(ResultCode resultCode, String message, Object detail) {
        return of(resultCode, message, detail, resultCode.requestOutcome());
    }

    /** 同一业务码存在不同执行事实时，允许异常实例提供精确的 requestOutcome。 */
    public static ApiFailure of(ResultCode resultCode, String message, Object detail,
                                RequestOutcome requestOutcome) {
        return new ApiFailure(resultCode.getCode(), message, detail,
                new FailureMeta(requestOutcome));
    }
}
