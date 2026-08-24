package com.zhiyi.common;

/**
 * 不可变的统一 API 响应。
 */
public record Result<T>(int code, String message, T data) {

    public static <T> Result<T> ok() {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> ok(String message, T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), message, data);
    }

    public static <T> Result<T> fail(ResultCode code) {
        return new Result<>(code.getCode(), code.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode code, String message) {
        return new Result<>(code.getCode(), message, null);
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /** 失败响应携带冲突详情（如资料 409 时的服务端最新资料）。 */
    public static <T> Result<T> fail(int code, String message, T detail) {
        return new Result<>(code, message, detail);
    }
}
