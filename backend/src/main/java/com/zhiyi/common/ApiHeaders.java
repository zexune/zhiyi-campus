package com.zhiyi.common;

/**
 * 后端公共 HTTP 头常量：订单控制器（@RequestHeader）、CORS 配置
 * （allowedHeaders/exposedHeaders）与 OpenAPI 声明必须引用同一来源，
 * 禁止在各处手写字符串导致漂移。
 */
public final class ApiHeaders {

    /** 资金操作幂等键请求头（36 位 UUID）。 */
    public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

    /** 允许退避的业务失败附带的建议重试秒数（HTTP 标准响应头）。 */
    public static final String RETRY_AFTER = "Retry-After";

    private ApiHeaders() {
    }
}
