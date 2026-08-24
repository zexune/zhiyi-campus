package com.zhiyi.common;

import lombok.Getter;

/**
 * 业务异常，抛出后被 GlobalExceptionHandler 统一处理
 */
@Getter
public class BusinessException extends RuntimeException {
    private final int code;

    /** 可选的冲突详情（如资料乐观并发 409 时携带服务端最新资料，前端展示并要求确认合并）。 */
    private transient Object conflictDetail;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException withConflictDetail(Object detail) {
        this.conflictDetail = detail;
        return this;
    }
}
