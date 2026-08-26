package com.zhiyi.common;

import com.zhiyi.common.annotation.BusinessErrors;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Arrays;

/**
 * operation 业务错误契约校验（P4-7）：请求实际抛出的 {@link BusinessException}
 * 必须出现在该 operation 的 {@code @BusinessErrors} 声明中（或属于通用参数
 * 校验码），否则 OpenAPI 文档与运行行为漂移。
 *
 * strict 模式（测试环境打开）直接抛错使契约测试失败；生产环境记录
 * 高优先级契约违约日志，不改变既有失败响应。
 */
@Slf4j
@Component
public class BusinessErrorContractVerifier {

    /** 通用参数/内容校验码：不属于任何特有业务分支，所有 operation 隐式允许。 */
    private static final ResultCode[] IMPLICITLY_ALLOWED = {ResultCode.BAD_REQUEST};

    private final boolean strict;

    public BusinessErrorContractVerifier(@Value("${zhiyi.contract.strict-business-errors:false}") boolean strict) {
        this.strict = strict;
    }

    public void verify(HttpServletRequest request, BusinessException exception) {
        Object handler = request == null ? null
                : request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        ResultCode actual = exception.getResultCode();
        if (isAllowed(handlerMethod, actual)) {
            return;
        }
        String violation = "operation " + handlerMethod.getMethod() + " 抛出了未声明的业务码 "
                + actual.getCode() + "（" + actual.getMessage() + "），请补充 @BusinessErrors 声明";
        if (strict) {
            throw new IllegalStateException("契约违约：" + violation);
        }
        log.error("契约违约：{}", violation);
    }

    private boolean isAllowed(HandlerMethod handlerMethod, ResultCode actual) {
        for (ResultCode allowed : IMPLICITLY_ALLOWED) {
            if (allowed == actual) {
                return true;
            }
        }
        BusinessErrors annotation = handlerMethod.getMethodAnnotation(BusinessErrors.class);
        if (annotation == null) {
            return false;
        }
        return Arrays.stream(annotation.value()).anyMatch(declared -> declared == actual);
    }
}
