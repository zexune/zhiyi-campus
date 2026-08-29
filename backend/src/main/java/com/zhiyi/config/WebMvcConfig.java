package com.zhiyi.config;

import com.zhiyi.common.ApiHeaders;
import com.zhiyi.interceptor.JwtInterceptor;
import com.zhiyi.interceptor.RoleInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RoleInterceptor roleInterceptor;
    private final String[] allowedOrigins;
    private final String uploadLocation;

    public WebMvcConfig(JwtInterceptor jwtInterceptor,
                        RoleInterceptor roleInterceptor,
                        @Value("${zhiyi.cors.allowed-origins}") String[] allowedOrigins,
                        @Value("${zhiyi.upload-path:./uploads}") String uploadPath) {
        this.jwtInterceptor = jwtInterceptor;
        this.roleInterceptor = roleInterceptor;
        this.allowedOrigins = allowedOrigins.clone();
        // 与 LocalImageStorage 共用同一属性并同样解析为绝对路径，保证读写同源；
        // file: 目录位置必须以 / 结尾（目录尚不存在时 toUri 不补斜杠，会静默 404）
        String uri = Path.of(uploadPath).toAbsolutePath().normalize().toUri().toString();
        this.uploadLocation = uri.endsWith("/") ? uri : uri + "/";
    }

    /**
     * CORS 跨域（前端分离部署时用）。
     * allowCredentials=true：登录凭证迁移到 httpOnly Cookie 后，跨源部署的前端必须携带 Cookie；
     * allowedOrigins 为显式白名单（非通配），与凭证模式兼容。
     * 请求头放行资金操作的 X-Idempotency-Key；暴露 Retry-After 供浏览器 JS 读取退避建议。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type", "Accept", ApiHeaders.IDEMPOTENCY_KEY)
                .exposedHeaders(ApiHeaders.RETRY_AFTER)
                .allowCredentials(true)
                .maxAge(3600);
    }

    /**
     * JWT 拦截器 + 角色拦截器（顺序：先登录校验，后角色校验）。
     * 公开路由排除清单来自 {@link PublicEndpointPolicy} 单一策略源；
     * 动态公开路由（/api/user/{id}/card 等）由 JwtInterceptor 内按方法复核放行。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(PublicEndpointPolicy.staticExcludePatterns().toArray(String[]::new))
                .order(0);
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**")
                .order(1);
    }

    /**
     * 静态资源映射（商品图片等上传文件）。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadLocation);
    }
}
