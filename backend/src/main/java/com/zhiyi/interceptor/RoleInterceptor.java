package com.zhiyi.interceptor;

import com.zhiyi.common.WebResponseUtil;
import com.zhiyi.common.annotation.RoleRequired;
import com.zhiyi.common.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 角色权限拦截器 —— 在 JwtInterceptor 之后执行。
 * 除校验 {@link RoleRequired} 外，还强制隔离管理员与普通用户的 API 命名空间：
 * 管理员只能访问 {@code /api/admin/**}，普通用户不能访问管理接口。
 */
@Component
public class RoleInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (!contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }

        Object roleAttribute = request.getAttribute("role");
        String role = roleAttribute == null ? null : roleAttribute.toString();
        boolean adminApi = path.equals("/api/admin") || path.startsWith("/api/admin/");

        if (UserRole.ADMIN.code().equals(role) && !adminApi) {
            WebResponseUtil.writeJson(response, 403, 403, "管理员账号仅可访问管理后台");
            return false;
        }
        if (adminApi && role != null && !UserRole.ADMIN.code().equals(role)) {
            WebResponseUtil.writeJson(response, 403, 403, "普通用户无权访问管理后台");
            return false;
        }

        RoleRequired required = handlerMethod.getMethodAnnotation(RoleRequired.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RoleRequired.class);
        }
        if (required == null) {
            return true; // 未标注注解的接口不做角色限制
        }

        if (role == null || !required.value().code().equals(role)) {
            WebResponseUtil.writeJson(response, 403, 403, "权限不足");
            return false;
        }
        return true;
    }
}
