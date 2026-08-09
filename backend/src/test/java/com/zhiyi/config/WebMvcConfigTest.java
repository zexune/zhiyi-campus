package com.zhiyi.config;

import com.zhiyi.interceptor.JwtInterceptor;
import com.zhiyi.interceptor.RoleInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
class WebMvcConfigTest {

    @Test
    void jwtInterceptorStillAppliesToNonGetItemRoute() {
        JwtInterceptor jwtInterceptor = new JwtInterceptor(null, null);
        RoleInterceptor roleInterceptor = new RoleInterceptor();
        WebMvcConfig config = new WebMvcConfig(
                jwtInterceptor, roleInterceptor, new String[]{"http://localhost:3000"});
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();

        config.addInterceptors(registry);

        MappedInterceptor jwtMapping = registry.mappingFor(jwtInterceptor);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/item/42");
        ServletRequestPathUtils.parseAndCache(request);
        assertTrue(jwtMapping.matches(request),
                "PUT /api/item/{id} must reach JwtInterceptor instead of matching the public GET exclusion");
    }

    @Test
    void trendingAiTagsRouteRequiresLogin() {
        JwtInterceptor jwtInterceptor = new JwtInterceptor(null, null);
        RoleInterceptor roleInterceptor = new RoleInterceptor();
        WebMvcConfig config = new WebMvcConfig(
                jwtInterceptor, roleInterceptor, new String[]{"http://localhost:3000"});
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();

        config.addInterceptors(registry);

        MappedInterceptor jwtMapping = registry.mappingFor(jwtInterceptor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/item/ranking/tags");
        ServletRequestPathUtils.parseAndCache(request);
        assertTrue(jwtMapping.matches(request));
    }

    private static final class ExposedInterceptorRegistry extends InterceptorRegistry {
        MappedInterceptor mappingFor(HandlerInterceptor interceptor) {
            List<Object> registrations = getInterceptors();
            return registrations.stream()
                    .filter(MappedInterceptor.class::isInstance)
                    .map(MappedInterceptor.class::cast)
                    .filter(mapped -> mapped.getInterceptor() == interceptor)
                    .findFirst()
                    .orElseThrow();
        }
    }
}
