package com.zhiyi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI metadata and JWT authentication support for the interactive API documentation.
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";

    private static final Map<String, Set<PathItem.HttpMethod>> PUBLIC_OPERATIONS = Map.ofEntries(
            Map.entry("/api/auth/register", Set.of(PathItem.HttpMethod.POST)),
            Map.entry("/api/auth/login", Set.of(PathItem.HttpMethod.POST)),
            Map.entry("/api/auth/security-question", Set.of(PathItem.HttpMethod.GET)),
            Map.entry("/api/auth/security-questions", Set.of(PathItem.HttpMethod.GET)),
            Map.entry("/api/auth/reset-password", Set.of(PathItem.HttpMethod.POST)),
            Map.entry("/api/admin/auth/login", Set.of(PathItem.HttpMethod.POST)),
            Map.entry("/api/school/list", Set.of(PathItem.HttpMethod.GET)),
            Map.entry("/api/category/list", Set.of(PathItem.HttpMethod.GET)),
            Map.entry("/api/user/{id}/card", Set.of(PathItem.HttpMethod.GET)),
            Map.entry("/api/user/{id}/reputation", Set.of(PathItem.HttpMethod.GET))
    );

    @Bean
    public OpenAPI zhiyiCampusOpenApi() {
        SecurityScheme bearerScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("请输入用户端或管理端登录接口返回的 JWT，无需添加 Bearer 前缀");

        return new OpenAPI()
                .info(new Info()
                        .title("智易校园 API")
                        .description("校园交易平台后端接口文档")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_AUTH, bearerScheme))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * An empty operation-level security list overrides the global JWT requirement for public APIs.
     */
    @Bean
    public OpenApiCustomizer publicEndpointSecurityCustomizer() {
        return openApi -> PUBLIC_OPERATIONS.forEach((path, methods) -> {
            if (openApi.getPaths() == null) {
                return;
            }
            PathItem pathItem = openApi.getPaths().get(path);
            if (pathItem == null) {
                return;
            }
            Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
            methods.stream()
                    .map(operations::get)
                    .filter(operation -> operation != null)
                    .forEach(operation -> operation.setSecurity(List.of()));
        });
    }
}
