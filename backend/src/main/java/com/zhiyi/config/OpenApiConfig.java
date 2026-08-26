package com.zhiyi.config;

import com.zhiyi.common.ResultCode;
import com.zhiyi.common.annotation.BusinessErrors;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.headers.Header;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenAPI metadata, JWT authentication support and error contract generation
 * for the interactive API documentation.
 *
 * P3：信封 Schema 一律使用类型化构造（IntegerSchema/StringSchema/ObjectSchema/
 * typesItem）。泛型 {@code Schema<>().type(...)} 在 springdoc 的 OpenAPI 3.1
 * 序列化（Json31）下会丢失 type——契约测试校验最终序列化 JSON，不做内存对象断言。
 *
 * P4：错误契约按 operation 生成——公共错误（400/401/403/405/406/413/415/500）与
 * {@link BusinessErrors} 声明的业务码在同一 HTTP 状态下合并（不互相覆盖），
 * 每个响应状态携带 x-business-codes，含可退避码时声明可选 Retry-After 响应头
 * 与 x-retry-after-business-codes。404 不再是公共响应：资源不存在由各 operation
 * 的业务码显式声明。
 */
@Configuration
public class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";
    static final String API_FAILURE_SCHEMA = "ApiFailure";
    static final String API_SUCCESS_EVENT_TOPIC_NULLABLE = "ApiSuccessEventTopicNullable";
    /** "当前没有活动专题"是正常结果：active-topic 单独使用可空 EventTopic 表达。 */
    static final String ACTIVE_TOPIC_PATH = "/api/item/active-topic";

    /** 公共错误：所有受保护 operation 都可能产生的失败；404 不在其中（由业务码显式声明）。 */
    private static final Map<String, CommonFailure> COMMON_FAILURES = new LinkedHashMap<>(Map.of(
            "400", new CommonFailure("参数校验失败", List.of(ResultCode.BAD_REQUEST)),
            "403", new CommonFailure("权限不足或账户被拒绝", List.of(ResultCode.FORBIDDEN, ResultCode.USER_BANNED)),
            "405", new CommonFailure("请求方法不受支持", List.of(ResultCode.METHOD_NOT_ALLOWED)),
            "406", new CommonFailure("无法生成 Accept 指定的响应格式", List.of(ResultCode.NOT_ACCEPTABLE)),
            "413", new CommonFailure("请求体或上传文件超过服务端限制", List.of(ResultCode.PAYLOAD_TOO_LARGE)),
            "415", new CommonFailure("请求 Content-Type 与 endpoint 契约不匹配", List.of(ResultCode.UNSUPPORTED_MEDIA_TYPE)),
            "500", new CommonFailure("服务器内部错误（幂等处置 UNKNOWN）", List.of(ResultCode.SERVER_ERROR))
    ));
    private static final CommonFailure UNAUTHORIZED_FAILURE = new CommonFailure(
            "认证失败：Token 缺失/无效/过期或会话失效（响应已清除会话 Cookie）",
            List.of(ResultCode.UNAUTHORIZED, ResultCode.SESSION_INVALIDATED));

    private record CommonFailure(String description, List<ResultCode> codes) {
    }

    /** 单个响应状态的聚合结果：描述 + 业务码（公共 ∪ operation 声明）。 */
    private static final class StatusFailure {
        final List<String> descriptions = new ArrayList<>();
        final Set<ResultCode> codes = new LinkedHashSet<>();

        void addDescription(String description) {
            descriptions.add(description);
        }

        void addCodes(List<ResultCode> codesToAdd) {
            codes.addAll(codesToAdd);
        }
    }

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
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH, bearerScheme)
                        .addSchemas(API_FAILURE_SCHEMA, apiFailureSchema())
                        .addSchemas(API_SUCCESS_EVENT_TOPIC_NULLABLE, apiSuccessEventTopicNullable()))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    /**
     * An empty operation-level security list overrides the global JWT requirement for public APIs.
     */
    @Bean
    public OpenApiCustomizer publicEndpointSecurityCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            openApi.getPaths().forEach((path, pathItem) -> {
                Map<PathItem.HttpMethod, Operation> operations = pathItem.readOperationsMap();
                operations.forEach((method, operation) -> {
                    if (operation == null || operation.getSecurity() != null) {
                        return;
                    }
                    if (PublicEndpointPolicy.isPublicRequest(method.name(), path)) {
                        operation.setSecurity(List.of());
                    }
                });
            });
        };
    }

    /**
     * 成功信封收口（P3）：springdoc 反射生成的 ApiSuccess* Schema 统一修正——
     * code/message/data 全部 required；code 收窄为字面量 200；
     * ApiSuccessVoid.data 为显式 null；其余 data 保持非空 $ref。
     */
    @Bean
    public OpenApiCustomizer successEnvelopeCustomizer() {
        return openApi -> {
            Map<String, ?> schemas = openApi.getComponents().getSchemas();
            if (schemas == null) {
                return;
            }
            schemas.forEach((name, candidate) -> {
                if (!(candidate instanceof Schema<?> schema)) {
                    return;
                }
                if (!name.startsWith("ApiSuccess") || schema.getProperties() == null) {
                    return;
                }
                if (!schema.getProperties().containsKey("code")) {
                    return;
                }
                schema.addProperty("code", successCodeSchema());
                if (name.equals("ApiSuccessVoid")) {
                    schema.addProperty("data", new Schema<>().typesItem("null")
                            .description("无数据负载的显式 null"));
                }
                schema.setRequired(new ArrayList<>(List.of("code", "message", "data")));
            });
        };
    }

    /**
     * 错误契约生成（P4）：公共失败统一注入（公开端点不含 401/403），
     * {@link BusinessErrors} 声明的业务码按 ResultCode 绑定的 HTTP 状态归组，
     * 与同状态的公共错误合并为单一响应（不覆盖），并在 x-business-codes 扩展中
     * 机器可读地列出；包含可退避码的状态声明可选 Retry-After 头。
     */
    @Bean
    public OperationCustomizer errorContractCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() == null) {
                operation.setResponses(new ApiResponses());
            }
            boolean isPublic = PublicEndpointPolicy.isPublicHandlerMethod(handlerMethod);

            List<ResultCode> declared = List.of(declaredBusinessCodes(handlerMethod));
            Map<String, StatusFailure> byStatus = new LinkedHashMap<>();

            if (!isPublic) {
                StatusFailure failure = statusFailure(byStatus, "401");
                failure.addDescription(UNAUTHORIZED_FAILURE.description());
                failure.addCodes(UNAUTHORIZED_FAILURE.codes());
            }
            COMMON_FAILURES.forEach((status, common) -> {
                if (isPublic && ("401".equals(status) || "403".equals(status))) {
                    return;
                }
                StatusFailure failure = statusFailure(byStatus, status);
                failure.addDescription(common.description());
                failure.addCodes(common.codes());
            });
            for (ResultCode code : declared) {
                StatusFailure failure = statusFailure(byStatus, Integer.toString(code.getHttpStatus().value()));
                failure.addDescription(code.getCode() + "：" + code.getMessage());
                failure.addCodes(List.of(code));
            }

            byStatus.forEach((status, failure) ->
                    operation.getResponses().put(status, failureResponse(status, failure)));
            if (!declared.isEmpty()) {
                operation.addExtension("x-business-codes",
                        declared.stream().map(code -> String.valueOf(code.getCode())).toList());
                List<String> retryCodes = declared.stream()
                        .filter(code -> code.getRetryAfterSeconds() > 0)
                        .map(code -> String.valueOf(code.getCode()))
                        .toList();
                if (!retryCodes.isEmpty()) {
                    operation.addExtension("x-retry-after-business-codes", retryCodes);
                }
            }
            return operation;
        };
    }

    /**
     * active-topic 的 200 响应单独指向可空信封：data 为 EventTopic | null，
     * 不弱化管理端 EventTopic 写接口的非空契约。
     */
    @Bean
    public OperationCustomizer activeTopicNullableCustomizer() {
        return (operation, handlerMethod) -> {
            if (!ACTIVE_TOPIC_PATH.equals(fullPath(handlerMethod))) {
                return operation;
            }
            ApiResponse ok = new ApiResponse().description("当前活动专题；没有活动专题时 data 为 null")
                    .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE,
                            new MediaType().schema(new Schema<>().$ref(
                                    "#/components/schemas/" + API_SUCCESS_EVENT_TOPIC_NULLABLE))));
            operation.getResponses().put("200", ok);
            return operation;
        };
    }

    private static StatusFailure statusFailure(Map<String, StatusFailure> byStatus, String status) {
        return byStatus.computeIfAbsent(status, key -> new StatusFailure());
    }

    private String fullPath(HandlerMethod handlerMethod) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), RequestMapping.class);
        RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getMethod(), RequestMapping.class);
        if (methodMapping == null || methodMapping.path().length != 1) {
            return null;
        }
        // 类级映射可缺省（如 EventTopicController 在方法上直接写完整路径）
        String classPath = classMapping == null || classMapping.path().length == 0 ? "" : classMapping.path()[0];
        return classPath + methodMapping.path()[0];
    }

    private ResultCode[] declaredBusinessCodes(HandlerMethod handlerMethod) {
        BusinessErrors annotation = handlerMethod.getMethodAnnotation(BusinessErrors.class);
        return annotation == null ? new ResultCode[0] : annotation.value();
    }

    private static ApiResponse failureResponse(String status, StatusFailure failure) {
        List<String> codeValues = failure.codes.stream().map(code -> String.valueOf(code.getCode())).toList();
        List<String> retryCodes = failure.codes.stream()
                .filter(code -> code.getRetryAfterSeconds() > 0)
                .map(code -> String.valueOf(code.getCode()))
                .toList();

        StringBuilder description = new StringBuilder(String.join("；", failure.descriptions));
        if (!retryCodes.isEmpty()) {
            description.append("。业务码 ").append(String.join("/", retryCodes))
                    .append(" 附 Retry-After 响应头（建议退避秒数，数值为字符串）");
        }

        MediaType mediaType = new MediaType().schema(
                new Schema<>().$ref("#/components/schemas/" + API_FAILURE_SCHEMA));
        ApiResponse response = new ApiResponse()
                .description(description.toString())
                .content(new Content().addMediaType(org.springframework.http.MediaType.APPLICATION_JSON_VALUE, mediaType));
        response.addExtension("x-business-codes", codeValues);
        if (!retryCodes.isEmpty()) {
            response.addExtension("x-retry-after-business-codes", retryCodes);
            Header retryAfter = new Header()
                    .description("建议退避秒数（业务码 " + String.join("/", retryCodes) + " 携带）");
            retryAfter.setSchema(new StringSchema().pattern("^[0-9]+$"));
            response.addHeaderObject(HttpHeaders.RETRY_AFTER, retryAfter);
        }
        return response;
    }

    /**
     * ApiFailure 手工 Schema：信封形状由代码唯一决定，不依赖反射推断。
     * 全部字段 required；data 无详情时 wire 上为显式 null，schema 允许任意 JSON；
     * meta.requestOutcome 为必填枚举；不包含 body 级 retryAfterSeconds
     * （退避建议只走标准 Retry-After 响应头）。
     */
    private static Schema<?> apiFailureSchema() {
        StringSchema requestOutcome = new StringSchema();
        requestOutcome.setEnum(List.of("REJECTED", "PROCESSING", "UNKNOWN"));
        requestOutcome.setDescription("幂等处置：REJECTED=明确拒绝可清键；PROCESSING=服务端处理中；UNKNOWN=结果不明");

        ObjectSchema meta = new ObjectSchema();
        meta.addProperty("requestOutcome", requestOutcome);
        meta.setRequired(List.of("requestOutcome"));
        meta.setDescription("失败元数据（必填）");

        Schema<?> data = new Schema<>()
                .description("错误详情（如资料 409 时的服务端最新资料）；无详情时 wire 上为显式 null");

        ObjectSchema failure = new ObjectSchema();
        failure.addProperty("code", new IntegerSchema());
        failure.addProperty("message", new StringSchema());
        failure.addProperty("data", data);
        failure.addProperty("meta", meta);
        failure.setRequired(new ArrayList<>(List.of("code", "message", "data", "meta")));
        return failure;
    }

    /** active-topic 专用可空信封：data = EventTopic | null。 */
    private static Schema<?> apiSuccessEventTopicNullable() {
        Schema<?> data = new Schema<>().anyOf(List.of(
                new Schema<>().$ref("#/components/schemas/EventTopicResponse"),
                new Schema<>().typesItem("null")));
        ObjectSchema envelope = new ObjectSchema();
        envelope.addProperty("code", successCodeSchema());
        envelope.addProperty("message", new StringSchema());
        envelope.addProperty("data", data);
        envelope.setRequired(new ArrayList<>(List.of("code", "message", "data")));
        return envelope;
    }

    private static IntegerSchema successCodeSchema() {
        IntegerSchema code = new IntegerSchema();
        code.setEnum(List.of(200));
        code.setDefault(200);
        return code;
    }
}
