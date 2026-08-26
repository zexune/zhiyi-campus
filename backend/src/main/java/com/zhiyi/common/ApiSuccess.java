package com.zhiyi.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 成功信封（P1-1 类型分离）：wire 形状保持 {code,message,data} 兼容格式，
 * 但成功对象在类型层不可能携带非 200 成功码，也不可能被塞进失败负载。
 */
public record ApiSuccess<T>(int code, String message,
                            @JsonInclude(JsonInclude.Include.ALWAYS) T data) {

    public ApiSuccess {
        if (code != ResultCode.SUCCESS.getCode()) {
            throw new IllegalArgumentException("成功信封的 code 必须是 200，得到：" + code);
        }
    }

    public static <T> ApiSuccess<T> ok() {
        return new ApiSuccess<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), null);
    }

    public static <T> ApiSuccess<T> ok(T data) {
        return new ApiSuccess<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> ApiSuccess<T> ok(String message, T data) {
        return new ApiSuccess<>(ResultCode.SUCCESS.getCode(), message, data);
    }
}
