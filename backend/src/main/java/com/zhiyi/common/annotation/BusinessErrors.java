package com.zhiyi.common.annotation;

import com.zhiyi.common.ResultCode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 声明该 endpoint 特有的业务错误码（P1-4）。
 *
 * OpenAPI 的 OperationCustomizer 据此在对应 operation 上生成
 * 400/401/403/404/405/406/409/413/415/422/429/500 错误响应（引用统一 ApiFailure Schema），
 * 公共错误（400 参数 / 401 认证 / 403 权限 / 405 方法 / 406 Accept /
 * 413 载荷 / 415 Content-Type / 500）由定制器统一注入；404 不豁免 operation
 * 声明，资源不存在必须由 endpoint 通过业务码显式登记。
 * 业务错误由 endpoint 显式声明——契约测试会校验声明的每个业务码
 * 都出现在该 operation 的 OpenAPI 文档中。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessErrors {

    ResultCode[] value() default {};
}
