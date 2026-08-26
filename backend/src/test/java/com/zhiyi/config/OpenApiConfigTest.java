package com.zhiyi.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.RequestBody;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        Operation publicLogout = new Operation();
        Operation protectedProfile = new Operation();
        OpenAPI openApi = config.zhiyiCampusOpenApi().paths(new Paths()
                .addPathItem("/api/auth/login", new PathItem().post(publicLogin))
                .addPathItem("/api/user/{id}/card", new PathItem().get(publicUserCard))
                .addPathItem("/api/auth/logout", new PathItem().post(publicLogout))
                .addPathItem("/api/user/profile", new PathItem().get(protectedProfile)));

        config.publicEndpointSecurityCustomizer().customise(openApi);

        assertAll(
                () -> assertNotNull(publicLogin.getSecurity()),
                () -> assertTrue(publicLogin.getSecurity().isEmpty()),
                () -> assertNotNull(publicUserCard.getSecurity()),
                () -> assertTrue(publicUserCard.getSecurity().isEmpty()),
                () -> assertNotNull(publicLogout.getSecurity()),
                () -> assertTrue(publicLogout.getSecurity().isEmpty(), "幂等登出应公开"),
                () -> assertNull(protectedProfile.getSecurity())
        );
    }

    /** P1-4 契约：声明的每个业务码都出现在相应 operation 的 OpenAPI 文档中。 */
    @Test
    void declaredBusinessCodesAppearInOperationResponses() throws Exception {
        HandlerMethod handler = new HandlerMethod(new FixtureController(),
                FixtureController.class.getMethod("create"));

        Operation operation = config.errorContractCustomizer().customize(new Operation(), handler);

        ApiResponse conflict = operation.getResponses().get("409");
        assertNotNull(conflict, "3001 应映射到 409 响应");
        assertTrue(conflict.getDescription().contains("3001"), conflict.getDescription());
        assertTrue(conflict.getDescription().contains("余额不足"), conflict.getDescription());
        ApiResponse tooManyRequests = operation.getResponses().get("429");
        assertNotNull(tooManyRequests, "3004 应映射到 429 响应");
        assertTrue(tooManyRequests.getDescription().contains("3004"), tooManyRequests.getDescription());
        assertNotNull(tooManyRequests.getHeaders().get("Retry-After"), "含可退避码的状态应声明 Retry-After 头");
        assertTrue(operation.getExtensions().get("x-business-codes").toString().contains("3001"));
        assertTrue(operation.getExtensions().get("x-business-codes").toString().contains("3004"));
        assertTrue(operation.getExtensions().get("x-retry-after-business-codes").toString().contains("3004"));
        // 公共错误统一注入
        assertNotNull(operation.getResponses().get("401"));
        assertNotNull(operation.getResponses().get("500"));
        assertNotNull(operation.getResponses().get("405"), "方法不支持必须有真实 405 响应");
        assertNotNull(operation.getResponses().get("406"), "Accept 不可满足必须有真实 406 响应");
        assertNotNull(operation.getResponses().get("413"), "载荷过大必须有真实 413 响应");
        assertNotNull(operation.getResponses().get("415"), "Content-Type 不支持必须有真实 415 响应");
        assertNull(operation.getResponses().get("404"), "404 不再是公共响应，由业务码显式声明");
        // 所有错误响应统一引用 ApiFailure Schema
        operation.getResponses().forEach((status, response) -> {
            if (status.startsWith("2")) return;
            assertTrue(response.getContent().get("application/json").getSchema()
                            .get$ref().endsWith("ApiFailure"),
                    status + " 应引用 ApiFailure");
        });
    }

    /** P4：同一状态码下公共错误与业务错误合并且不互相覆盖。 */
    @Test
    void commonAndBusinessErrorsMergeUnderSameStatus() throws Exception {
        HandlerMethod handler = new HandlerMethod(new FixtureController(),
                FixtureController.class.getMethod("banned"));

        Operation operation = config.errorContractCustomizer().customize(new Operation(), handler);

        ApiResponse forbidden = operation.getResponses().get("403");
        assertNotNull(forbidden);
        assertTrue(forbidden.getDescription().contains("权限不足"), forbidden.getDescription());
        assertTrue(forbidden.getDescription().contains("1003"), forbidden.getDescription());
        assertTrue(forbidden.getExtensions().get("x-business-codes").toString().contains("403"));
        assertTrue(forbidden.getExtensions().get("x-business-codes").toString().contains("1003"));
    }

    @Test
    void publicOperationOmitsAuthFailures() throws Exception {
        HandlerMethod handler = new HandlerMethod(new PublicFixtureController(),
                PublicFixtureController.class.getMethod("login"));

        Operation operation = config.errorContractCustomizer().customize(new Operation(), handler);

        assertNull(operation.getResponses().get("401"), "公开端点不声明 401");
        assertNull(operation.getResponses().get("403"), "公开端点不声明 403");
        assertNotNull(operation.getResponses().get("400"));
        assertNotNull(operation.getResponses().get("500"));
    }

    @Test
    void auditedEmptyOperationCarriesCommonFailuresOnly() throws Exception {
        HandlerMethod handler = new HandlerMethod(new FixtureController(),
                FixtureController.class.getMethod("auditedEmpty"));

        Operation operation = config.errorContractCustomizer().customize(new Operation(), handler);

        assertNull(operation.getExtensions(), "审计为空的 operation 不应有 x-business-codes");
        assertFalse(operation.getResponses().containsKey("429"));
        assertFalse(operation.getResponses().containsKey("409"));
        assertNotNull(operation.getResponses().get("400"));
    }

    /**
     * P3：断言最终序列化的 JSON（springdoc 用 Json31 输出 /v3/api-docs），
     * 不做内存对象断言——泛型 Schema.type 在 Json31 下会丢失 type 正是历史 bug。
     */
    @Test
    void serializedApiFailureSchemaKeepsTypesAndRequiredFields() throws Exception {
        OpenAPI openApi = config.zhiyiCampusOpenApi();
        String json = Json31.mapper().writeValueAsString(openApi);
        JsonNode root = Json31.mapper().readTree(json);
        JsonNode failure = root.get("components").get("schemas").get("ApiFailure");

        JsonNode code = failure.get("properties").get("code");
        assertEquals("integer", code.get("type").asText(), "code 必须保留 type:integer：" + code);
        assertEquals("int32", code.get("format").asText());
        assertEquals("string", failure.get("properties").get("message").get("type").asText());
        assertEquals("string", failure.get("properties").get("meta").get("properties")
                .get("requestOutcome").get("type").asText());
        assertTrue(failure.get("properties").get("meta").get("properties").get("requestOutcome")
                .get("enum").toString().contains("REJECTED"));
        assertTrue(failure.get("required").toString().contains("data"), "data 必须 required");
        assertTrue(failure.get("required").toString().contains("meta"), "meta 必须 required");
        assertTrue(failure.get("properties").get("meta").get("required").toString().contains("requestOutcome"));
        assertNull(failure.get("properties").get("meta").get("properties").get("retryAfterSeconds"),
                "body 级 retryAfterSeconds 必须删除（只走 Retry-After 头）");
        JsonNode data = failure.get("properties").get("data");
        assertNull(data.get("type"), "data 允许任意 JSON（无类型约束）：" + data);
    }

    /** P3：成功信封收口后（serialized）code=200 字面量、ApiSuccessVoid.data 显式 null、全字段 required。 */
    @Test
    void serializedSuccessEnvelopesAreNarrowed() throws Exception {
        OpenAPI openApi = config.zhiyiCampusOpenApi();
        // 模拟 springdoc 反射生成的 ApiSuccess 信封（无 required、code 未收窄）
        openApi.getComponents().addSchemas("ApiSuccessLoginVO", new ObjectSchema()
                .addProperty("code", new IntegerSchema())
                .addProperty("message", new StringSchema())
                .addProperty("data", new Schema<>().$ref("#/components/schemas/LoginVO")));
        openApi.getComponents().addSchemas("ApiSuccessVoid", new ObjectSchema()
                .addProperty("code", new IntegerSchema())
                .addProperty("message", new StringSchema())
                .addProperty("data", new Schema<>()));

        config.successEnvelopeCustomizer().customise(openApi);

        JsonNode root = Json31.mapper().readTree(Json31.mapper().writeValueAsString(openApi));
        JsonNode schemas = root.get("components").get("schemas");

        JsonNode login = schemas.get("ApiSuccessLoginVO");
        assertEquals("[200]", login.get("properties").get("code").get("enum").toString(),
                "code 收窄为字面量 200");
        assertEquals(login.get("required").toString().contains("data"), true, "data 必须 required");
        assertTrue(login.get("required").toString().contains("message"));

        JsonNode voidEnvelope = schemas.get("ApiSuccessVoid");
        assertEquals("null", voidEnvelope.get("properties").get("data").get("type").asText(),
                "ApiSuccessVoid.data 必须是显式 null 类型");
    }

    /** P3：active-topic 使用独立的可空 EventTopic 表达。 */
    @Test
    void activeTopicUsesDedicatedNullableEnvelope() throws Exception {
        JsonNode root = Json31.mapper().readTree(
                Json31.mapper().writeValueAsString(config.zhiyiCampusOpenApi()));
        JsonNode data = root.get("components").get("schemas")
                .get("ApiSuccessEventTopicNullable").get("properties").get("data");
        String dataJson = data.toString();
        assertTrue(dataJson.contains("$ref") && dataJson.contains("EventTopicResponse"), dataJson);
        assertTrue(dataJson.contains("null"), "data 必须允许 null：" + dataJson);
    }

    /**
     * 3.1 multipart 上传契约：错误契约定制器注入公共失败响应时不得破坏
     * springdoc 生成的 required multipart requestBody（required=true、
     * content 仅 multipart/form-data、schema.required 含 file、file 为 binary）。
     */
    @Test
    void errorContractCustomizerPreservesMultipartRequestBody() throws Exception {
        HandlerMethod handler = new HandlerMethod(new FixtureController(),
                FixtureController.class.getMethod("upload"));
        Operation operation = new Operation()
                .requestBody(new RequestBody().required(true)
                        .content(new Content().addMediaType("multipart/form-data",
                                new MediaType().schema(new ObjectSchema()
                                        .addProperty("file", new BinarySchema())
                                        .addRequiredItem("file")))));

        Operation customized = config.errorContractCustomizer().customize(operation, handler);

        JsonNode root = Json31.mapper().readTree(Json31.mapper().writeValueAsString(customized));
        JsonNode requestBody = root.get("requestBody");
        assertNotNull(requestBody, "multipart requestBody 不得被定制器移除");
        assertTrue(requestBody.get("required").asBoolean(), "requestBody.required 必须保持 true");
        JsonNode content = requestBody.get("content");
        assertEquals(1, content.size(), "content 只应包含一个 media type");
        assertTrue(content.has("multipart/form-data"), "media type 必须是 multipart/form-data");
        JsonNode schema = content.get("multipart/form-data").get("schema");
        assertTrue(schema.get("required").toString().contains("file"), "schema.required 必须包含 file");
        assertEquals("string", schema.get("properties").get("file").get("type").asText());
        assertEquals("binary", schema.get("properties").get("file").get("format").asText());
        // 公共错误照常注入，不影响成功侧 multipart 契约
        assertNotNull(customized.getResponses().get("400"));
        assertNotNull(customized.getResponses().get("405"));
        assertNotNull(customized.getResponses().get("406"));
        assertNotNull(customized.getResponses().get("413"));
        assertNotNull(customized.getResponses().get("415"));
        assertNotNull(customized.getResponses().get("500"));
    }

    @RequestMapping("/api/order")
    static class FixtureController {

        @PostMapping("/create")
        @BusinessErrors({ResultCode.BALANCE_NOT_ENOUGH, ResultCode.TRADE_BUSY})
        public String create() {
            return "";
        }

        @PostMapping("/upload")
        @BusinessErrors(ResultCode.SERVER_ERROR)
        public String upload() {
            return "";
        }

        @PostMapping("/banned")
        @BusinessErrors(ResultCode.USER_BANNED)
        public String banned() {
            return "";
        }

        /** 审计后确认为空：显式空注解。 */
        @PostMapping("/audited-empty")
        @BusinessErrors
        public String auditedEmpty() {
            return "";
        }
    }

    @RequestMapping("/api/auth")
    static class PublicFixtureController {

        @PostMapping("/login")
        public String login() {
            return "";
        }
    }
}
