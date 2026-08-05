package com.zhiyi.config;

import com.zhiyi.interceptor.JwtInterceptor;
import com.zhiyi.interceptor.RoleInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RoleInterceptor roleInterceptor;

    public WebMvcConfig(JwtInterceptor jwtInterceptor, RoleInterceptor roleInterceptor) {
        this.jwtInterceptor = jwtInterceptor;
        this.roleInterceptor = roleInterceptor;
    }

    /**
     * CORS 跨域（前端分离部署时用）
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /**
     * JWT 拦截器 + 角色拦截器（顺序：先登录校验，后角色校验）
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/api/**")                       // 拦截所有 API
                .excludePathPatterns(
                        "/api/auth/register",                     // 注册
                        "/api/auth/login",                        // 登录
                        "/api/auth/security-question",            // 获取密保问题
                        "/api/auth/security-questions",           // 预设密保问题列表
                        "/api/auth/reset-password",               // 重置密码
                        "/api/school/list",                       // 学校列表（注册/资料页下拉）
                        "/api/category/list"                      // 分类列表
                ).order(0);
        registry.addInterceptor(roleInterceptor)
                .addPathPatterns("/api/**")
                .order(1);
    }

    /**
     * 静态资源映射（商品图片等上传文件）
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:./uploads/");
    }
}
