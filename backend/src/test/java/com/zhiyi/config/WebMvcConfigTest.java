package com.zhiyi.config;

import com.zhiyi.common.WebResponseUtil;
import com.zhiyi.interceptor.JwtInterceptor;
import com.zhiyi.interceptor.RoleInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.StaticApplicationContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.resource.ResourceHttpRequestHandler;
import org.springframework.web.util.ServletRequestPathUtils;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
class WebMvcConfigTest {

    private static WebResponseUtil webResponseUtil() {
        return new WebResponseUtil(JsonMapper.builder().build());
    }

    @Test
    void jwtInterceptorStillAppliesToNonGetItemRoute() {
        JwtInterceptor jwtInterceptor = new JwtInterceptor(null, null, null, webResponseUtil());
        RoleInterceptor roleInterceptor = new RoleInterceptor(webResponseUtil());
        WebMvcConfig config = new WebMvcConfig(
                jwtInterceptor, roleInterceptor, new String[]{"http://localhost:3000"}, "./uploads");
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();

        config.addInterceptors(registry);

        MappedInterceptor jwtMapping = registry.mappingFor(jwtInterceptor);
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/api/item/42");
        ServletRequestPathUtils.parseAndCache(request);
        assertTrue(jwtMapping.matches(request),
                "PUT /api/item/{id} must reach JwtInterceptor instead of matching the public GET exclusion");
    }

    @Test
    void trendingTagsRouteRequiresLogin() {
        JwtInterceptor jwtInterceptor = new JwtInterceptor(null, null, null, webResponseUtil());
        RoleInterceptor roleInterceptor = new RoleInterceptor(webResponseUtil());
        WebMvcConfig config = new WebMvcConfig(
                jwtInterceptor, roleInterceptor, new String[]{"http://localhost:3000"}, "./uploads");
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();

        config.addInterceptors(registry);

        MappedInterceptor jwtMapping = registry.mappingFor(jwtInterceptor);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/item/ranking/tags");
        ServletRequestPathUtils.parseAndCache(request);
        assertTrue(jwtMapping.matches(request));
    }

    @Test
    void adminLoginIsPublicButAdminPasswordChangeRequiresToken() {
        JwtInterceptor jwtInterceptor = new JwtInterceptor(null, null, null, webResponseUtil());
        RoleInterceptor roleInterceptor = new RoleInterceptor(webResponseUtil());
        WebMvcConfig config = new WebMvcConfig(
                jwtInterceptor, roleInterceptor, new String[]{"http://localhost:3000"}, "./uploads");
        ExposedInterceptorRegistry registry = new ExposedInterceptorRegistry();
        config.addInterceptors(registry);
        MappedInterceptor jwtMapping = registry.mappingFor(jwtInterceptor);

        MockHttpServletRequest login = new MockHttpServletRequest("POST", "/api/admin/auth/login");
        ServletRequestPathUtils.parseAndCache(login);
        assertFalse(jwtMapping.matches(login));

        MockHttpServletRequest changePassword = new MockHttpServletRequest(
                "PUT", "/api/admin/auth/change-password");
        ServletRequestPathUtils.parseAndCache(changePassword);
        assertTrue(jwtMapping.matches(changePassword));
    }

    @Test
    void uploadLocationEndsWithSlashEvenWhenDirectoryDoesNotExistYet(@TempDir Path tempDir) throws Exception {
        Path missingDir = tempDir.resolve("not-yet-created");

        String location = locationOf(configWithUploadPath(missingDir.toString()));

        assertTrue(location.startsWith("file:"));
        assertTrue(location.endsWith("/not-yet-created/"),
                "file: 目录位置必须以 / 结尾：目录尚不存在时 toUri() 不补斜杠，静态资源会静默 404");
    }

    @Test
    void uploadLocationMatchesStorageSideResolutionForExistingDirectory(@TempDir Path tempDir) throws Exception {
        String location = locationOf(configWithUploadPath(tempDir.toString()));

        assertEquals(tempDir.toUri().toURL().toString(), location,
                "静态映射必须与 LocalImageStorage 解析到同一目录（读写同源）");
    }

    @Test
    void uploadLocationNormalizesDotDotSegments(@TempDir Path tempDir) throws Exception {
        String location = locationOf(configWithUploadPath(tempDir.resolve("segment/../uploads").toString()));

        assertFalse(location.contains("/../"));
        assertTrue(location.endsWith("/uploads/"));
    }

    @Test
    void servesFileUploadedAfterStartupWhenDirectoryWasMissingAtBoot(@TempDir Path tempDir) throws Exception {
        Path uploadDir = tempDir.resolve("late-created");
        WebMvcConfig config = configWithUploadPath(uploadDir.toString());
        ExposedResourceHandlerRegistry registry = new ExposedResourceHandlerRegistry();
        config.addResourceHandlers(registry);
        SimpleUrlHandlerMapping mapping = registry.handlerMapping();

        byte[] content = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
        Files.createDirectories(uploadDir);
        Files.write(uploadDir.resolve("logo.png"), content);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/uploads/logo.png");
        ServletRequestPathUtils.parseAndCache(request);
        HandlerExecutionChain chain = mapping.getHandler(request);
        assertNotNull(chain, "/uploads/** 必须映射到静态资源处理器");
        assertTrue(chain.getHandler() instanceof ResourceHttpRequestHandler);
        // 生产环境由链上拦截器在 preHandle 阶段设置该属性（DispatcherServlet 驱动，
        // Spring 7 起该方法为包私有），这里等价模拟：处理器按模式内路径解析资源
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, "logo.png");

        MockHttpServletResponse response = new MockHttpServletResponse();
        ((HttpRequestHandler) chain.getHandler()).handleRequest(request, response);

        assertEquals(200, response.getStatus());
        assertArrayEquals(content, response.getContentAsByteArray());
    }

    private static WebMvcConfig configWithUploadPath(String uploadPath) {
        return new WebMvcConfig(
                new JwtInterceptor(null, null, null, webResponseUtil()),
                new RoleInterceptor(webResponseUtil()),
                new String[]{"http://localhost:3000"}, uploadPath);
    }

    private static String locationOf(WebMvcConfig config) throws Exception {
        return uploadsHandlerOf(config).getLocations().get(0).getURL().toString();
    }

    private static ResourceHttpRequestHandler uploadsHandlerOf(WebMvcConfig config) {
        ExposedResourceHandlerRegistry registry = new ExposedResourceHandlerRegistry();
        config.addResourceHandlers(registry);
        Object handler = registry.handlerMapping().getUrlMap().get("/uploads/**");
        assertTrue(handler instanceof ResourceHttpRequestHandler,
                "/uploads/** 必须注册为静态资源处理器");
        return (ResourceHttpRequestHandler) handler;
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

    /** getHandlerMapping() 为 protected：以子类暴露，拿到真实的 SimpleUrlHandlerMapping 做断言/请求 */
    private static final class ExposedResourceHandlerRegistry extends ResourceHandlerRegistry {

        private final ApplicationContext applicationContext;

        ExposedResourceHandlerRegistry() {
            this(new StaticApplicationContext());
        }

        private ExposedResourceHandlerRegistry(ApplicationContext applicationContext) {
            super(applicationContext, new MockServletContext(), new ContentNegotiationManager());
            this.applicationContext = applicationContext;
        }

        SimpleUrlHandlerMapping handlerMapping() throws BeansException {
            SimpleUrlHandlerMapping mapping = (SimpleUrlHandlerMapping) getHandlerMapping();
            // 生产环境由 DispatcherServlet 注入 ApplicationContext 后才触发
            // registerHandlers；测试里手动补上，否则 urlMap 只是存着未注册
            mapping.setApplicationContext(applicationContext);
            return mapping;
        }
    }
}
