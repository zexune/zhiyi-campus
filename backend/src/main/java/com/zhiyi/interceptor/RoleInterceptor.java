package com.zhiyi.interceptor;

import com.zhiyi.common.WebResponseUtil;
import com.zhiyi.common.annotation.RoleRequired;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色权限拦截器 —— 在 JwtInterceptor 之后执行。
 * 读取 Controller 方法/类上的 @RoleRequired 注解，与 JWT 中的角色比对。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RoleRequired required = handlerMethod.getMethodAnnotation(RoleRequired.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RoleRequired.class);
        }
        if (required == null) {
            return true; // 未标注注解的接口不做角色限制
        }

        Object role = request.getAttribute("role");
        if (role == null || !required.value().equals(role.toString())) {
            WebResponseUtil.writeJson(response, 403, 403, "权限不足");
            return false;
        }
        return true;
    }
}
