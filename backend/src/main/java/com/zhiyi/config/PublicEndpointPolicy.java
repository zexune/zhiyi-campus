package com.zhiyi.config;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 公开端点单一策略源（P2-3）：拦截器排除清单（WebMvcConfig）、
 * 拦截器内动态放行（JwtInterceptor）与 OpenAPI 安全声明（OpenApiConfig）
 * 都从这里取数，消除"两份手写清单漂移"。
 *
 * 路径使用 OpenAPI 模板形式（{@code {id}}），匹配时模板段对任意单段放行；
 * 方法必须显式命中——动态公开路由不能因路径前缀把 PUT/DELETE 一并放行。
 */
public final class PublicEndpointPolicy {

    /** 一条公开路由：允许的 HTTP 方法 + 模板路径。 */
    public record PublicRoute(Set<RequestMethod> methods, String path) {
    }

    private static final List<PublicRoute> ROUTES = List.of(
            new PublicRoute(EnumSet.of(RequestMethod.POST), "/api/auth/register"),
            new PublicRoute(EnumSet.of(RequestMethod.POST), "/api/auth/login"),
            // 幂等登出：过期会话也可调用，只负责清除 Cookie，无业务副作用
            new PublicRoute(EnumSet.of(RequestMethod.POST), "/api/auth/logout"),
            new PublicRoute(EnumSet.of(RequestMethod.GET), "/api/auth/security-question"),
            new PublicRoute(EnumSet.of(RequestMethod.GET), "/api/auth/security-questions"),
            new PublicRoute(EnumSet.of(RequestMethod.POST), "/api/auth/reset-password"),
            new PublicRoute(EnumSet.of(RequestMethod.POST), "/api/admin/auth/login"),
            new PublicRoute(EnumSet.of(RequestMethod.POST), "/api/admin/auth/logout"),
            new PublicRoute(EnumSet.of(RequestMethod.GET), "/api/school/list"),
            new PublicRoute(EnumSet.of(RequestMethod.GET), "/api/category/list"),
            new PublicRoute(EnumSet.of(RequestMethod.GET), "/api/user/{id}/card"),
            new PublicRoute(EnumSet.of(RequestMethod.GET), "/api/user/{id}/reputation"));

    private PublicEndpointPolicy() {
    }

    /** 请求是否命中公开路由（方法 + 完整路径都匹配；{@code {x}} 段匹配任意单段）。 */
    public static boolean isPublicRequest(String method, String path) {
        RequestMethod requestMethod;
        try {
            requestMethod = RequestMethod.valueOf(method.toUpperCase());
        } catch (IllegalArgumentException unknownMethod) {
            return false;
        }
        for (PublicRoute route : ROUTES) {
            if (route.methods().contains(requestMethod) && matches(route.path(), path)) {
                return true;
            }
        }
        return false;
    }

    /** 模板路径逐段匹配：{@code {param}} 段对任意非空单段放行，其余段精确相等。 */
    static boolean matches(String pattern, String path) {
        String[] patternParts = pattern.split("/");
        String[] pathParts = path.split("/");
        if (patternParts.length != pathParts.length) {
            return false;
        }
        for (int i = 0; i < patternParts.length; i++) {
            String segment = patternParts[i];
            if (segment.startsWith("{") && segment.endsWith("}") && !segment.equals("{}")) {
                if (pathParts[i].isEmpty()) {
                    return false;
                }
                continue;
            }
            if (!segment.equals(pathParts[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * JwtInterceptor 的 excludePathPatterns：只含无路径参数的静态路由
     * （AntPathMatcher 不认识 {@code {id}} 模板）；动态公开路由仍进入拦截器，
     * 由 JwtInterceptor 按方法 + 路径复核放行。
     */
    public static List<String> staticExcludePatterns() {
        return ROUTES.stream()
                .map(PublicRoute::path)
                .filter(path -> !path.contains("{"))
                .distinct()
                .toList();
    }

    /** OpenAPI 侧判定：类级 + 方法级 @RequestMapping 推导模板路径后核对公开方法。 */
    public static boolean isPublicHandlerMethod(HandlerMethod handlerMethod) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), RequestMapping.class);
        RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequestMapping.class);
        if (classMapping == null || classMapping.path().length != 1
                || methodMapping == null || methodMapping.path().length != 1) {
            return false;
        }
        String fullPath = classMapping.path()[0] + methodMapping.path()[0];
        for (PublicRoute route : ROUTES) {
            if (!route.path().equals(fullPath)) {
                continue;
            }
            for (RequestMethod requestMethod : methodMapping.method()) {
                if (route.methods().contains(requestMethod)) {
                    return true;
                }
            }
        }
        return false;
    }
}
