package com.zhiyi.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    private final OpenApiConfig config = new OpenApiConfig();

    @Test
    void exposesApiMetadataAndJwtBearerScheme() {
        OpenAPI openApi = config.zhiyiCampusOpenApi();
        SecurityScheme bearerScheme = openApi.getComponents()
                .getSecuritySchemes()
                .get(OpenApiConfig.BEARER_AUTH);

        assertAll(
                () -> assertEquals("智易校园 API", openApi.getInfo().getTitle()),
                () -> assertEquals("v1", openApi.getInfo().getVersion()),
                () -> assertNotNull(bearerScheme),
                () -> assertEquals(SecurityScheme.Type.HTTP, bearerScheme.getType()),
                () -> assertEquals("bearer", bearerScheme.getScheme()),
                () -> assertEquals("JWT", bearerScheme.getBearerFormat()),
                () -> assertTrue(openApi.getSecurity().getFirst()
                        .containsKey(OpenApiConfig.BEARER_AUTH))
        );
    }

    @Test
    void publicOperationsOverrideGlobalSecurityRequirement() {
        Operation publicLogin = new Operation();
        Operation publicUserCard = new Operation();
        Operation protectedProfile = new Operation();
        OpenAPI openApi = config.zhiyiCampusOpenApi().paths(new Paths()
                .addPathItem("/api/auth/login", new PathItem().post(publicLogin))
                .addPathItem("/api/user/{id}/card", new PathItem().get(publicUserCard))
                .addPathItem("/api/user/profile", new PathItem().get(protectedProfile)));

        config.publicEndpointSecurityCustomizer().customise(openApi);

        assertAll(
                () -> assertNotNull(publicLogin.getSecurity()),
                () -> assertTrue(publicLogin.getSecurity().isEmpty()),
                () -> assertNotNull(publicUserCard.getSecurity()),
                () -> assertTrue(publicUserCard.getSecurity().isEmpty()),
                () -> assertNull(protectedProfile.getSecurity())
        );
    }
}
