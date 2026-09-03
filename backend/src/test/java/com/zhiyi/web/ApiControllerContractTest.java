package com.zhiyi.web;

import com.zhiyi.common.ApiHeaders;
import com.zhiyi.common.BusinessException;
import com.zhiyi.common.AuthTokenCookieWriter;
import com.zhiyi.common.BusinessErrorContractVerifier;
import com.zhiyi.common.GlobalExceptionHandler;
import com.zhiyi.common.ResultCode;
import com.zhiyi.config.OpenApiConfig;
import com.zhiyi.config.WebMvcConfig;
import com.zhiyi.interceptor.JwtInterceptor;
import com.zhiyi.interceptor.RoleInterceptor;
import com.zhiyi.module.admin.controller.AdminAuthController;
import com.zhiyi.module.admin.service.AdminAuthService;
import com.zhiyi.module.admin.service.AdminLineageService;
import com.zhiyi.module.admin.service.ViolationAppealService;
import com.zhiyi.module.admin.vo.AdminLoginVO;
import com.zhiyi.module.item.controller.ItemController;
import com.zhiyi.module.item.service.ItemPublishService;
import com.zhiyi.module.item.service.MarketplaceService;
import com.zhiyi.module.item.vo.UploadImageVO;
import com.zhiyi.module.social.controller.ChatController;
import com.zhiyi.module.social.service.ChatService;
import com.zhiyi.module.social.support.ChatEventBroadcaster;
import com.zhiyi.module.social.vo.ChatEventVO;
import com.zhiyi.module.trade.controller.OrderController;
import com.zhiyi.module.trade.service.OrderQueryService;
import com.zhiyi.module.trade.service.ReviewService;
import com.zhiyi.module.trade.service.TradingEntryService;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.user.controller.AuthController;
import com.zhiyi.module.user.controller.UserController;
import com.zhiyi.module.user.service.AccountSecurityService;
import com.zhiyi.module.user.service.AuthService;
import com.zhiyi.module.user.service.ReputationService;
import com.zhiyi.module.user.service.UserService;
import com.zhiyi.module.user.vo.LoginVO;
import com.zhiyi.module.user.vo.UserVO;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web MVC 切片契约测试：覆盖路由、JSON 序列化、Bean Validation 与统一异常响应。
 * 业务分支仍由纯单元测试负责，避免把控制器测试写成脆弱的大型上下文测试。
 *
 * strict 模式（zhiyi.contract.strict-business-errors=true）：operation 抛出
 * 未在 @BusinessErrors 声明的业务码时直接失败，固化 operation 级错误契约。
 *
 * springdoc 的 /v3/api-docs 也在本切片内可用（导入其配置类），上传等由
 * springdoc 反射生成的请求契约直接对实时文档断言，不与手写快照互相自证。
 */
@WebMvcTest
@ContextConfiguration(classes = ApiControllerContractTest.MvcTestConfiguration.class)
@TestPropertySource(properties = {
        "zhiyi.cors.allowed-origins=http://localhost:3000",
        "zhiyi.contract.strict-business-errors=true"
})
class ApiControllerContractTest {

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(org.springdoc.core.properties.SpringDocConfigProperties.class)
    @Import({
            AuthController.class,
            OrderController.class,
            AdminAuthController.class,
            ItemController.class,
            ChatController.class,
            UserController.class,
            GlobalExceptionHandler.class,
            BusinessErrorContractVerifier.class,
            WebMvcConfig.class,
            AuthTokenCookieWriter.class,
            OpenApiConfig.class,
            org.springdoc.core.configuration.SpringDocConfiguration.class,
            org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration.class
    })
    static class MvcTestConfiguration {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private TradingEntryService tradingEntryService;
    @MockitoBean private OrderQueryService orderQueryService;
    @MockitoBean private ReviewService reviewService;
    @MockitoBean private AdminAuthService adminAuthService;
    @MockitoBean private AccountSecurityService accountSecurityService;
    @MockitoBean private MarketplaceService marketplaceService;
    @MockitoBean private ItemPublishService itemPublishService;
    @MockitoBean private AdminLineageService lineageService;
    @MockitoBean private ViolationAppealService appealService;
    @MockitoBean private ChatService chatService;
    @MockitoBean private ChatEventBroadcaster chatEventBroadcaster;
    @MockitoBean private UserService userService;
    @MockitoBean private ReputationService reputationService;
    @MockitoBean private JwtInterceptor jwtInterceptor;
    @MockitoBean private RoleInterceptor roleInterceptor;

    @BeforeEach
    void allowRequestsThroughAlreadyUnitTestedInterceptors() throws Exception {
        when(jwtInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(roleInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("注册参数错误保持统一响应契约（data 显式 null），且不会进入业务层")
    void registrationValidationUsesUnifiedEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("studentId")))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"));

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("畸形 JSON 请求体映射为 400 而非兜底 500")
    void malformedJsonMapsToBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\": 1, "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"));

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("查询参数类型不匹配映射为 400 而非兜底 500")
    void requestParamTypeMismatchMapsToBadRequest() throws Exception {
        mockMvc.perform(get("/api/order/my-bought")
                        .requestAttr("userId", 7L)
                        .param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("page")))
                .andExpect(content().string(containsString("\"data\":null")));

        verify(orderQueryService, never()).getBoughtOrders(anyLong(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("登录成功同时下发 httpOnly 会话 Cookie，登出立即清除")
    void loginIssuesHttpOnlyCookieAndLogoutClearsIt() throws Exception {
        UserVO user = new UserVO();
        user.setId(7L);
        user.setRole("USER");
        when(authService.login(any())).thenReturn(new LoginVO("user.jwt.token", user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolId":1,"studentId":"20260001","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    org.junit.jupiter.api.Assertions.assertNotNull(setCookie, "登录应下发 Set-Cookie");
                    org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"));
                    org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("SameSite=Lax"));
                    org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Path=/api"));
                });

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(result -> {
                    String setCookie = result.getResponse().getHeader("Set-Cookie");
                    org.junit.jupiter.api.Assertions.assertNotNull(setCookie, "登出应清除 Cookie");
                    org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Max-Age=0"));
                });
    }

    @Test
    @DisplayName("普通用户登录成功响应只暴露稳定的 token 与用户摘要")
    void loginSuccessSerializesStableContract() throws Exception {
        UserVO user = new UserVO();
        user.setId(7L);
        user.setStudentId("20260001");
        user.setNickname("契约同学");
        user.setRole("USER");
        when(authService.login(any())).thenReturn(new LoginVO("user.jwt.token", user));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolId":1,"studentId":"20260001","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("登录成功"))
                .andExpect(jsonPath("$.data.token").value("user.jwt.token"))
                .andExpect(jsonPath("$.data.user.id").value(7))
                .andExpect(jsonPath("$.data.user.role").value("USER"));
    }

    @Test
    @DisplayName("下单成功响应保留订单金额与状态的 JSON 契约")
    void createOrderSerializesMoneyAndStatus() throws Exception {
        OrderVO order = new OrderVO();
        order.setId(41L);
        order.setItemId(9L);
        order.setBuyerId(7L);
        order.setSellerId(8L);
        order.setPrice(new BigDecimal("19.90"));
        order.setStatus("WAITING_MEET");
        when(tradingEntryService.createOrder(eq(7L), any(), anyString())).thenReturn(order);

        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
                        .header(ApiHeaders.IDEMPOTENCY_KEY, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("下单成功，资金已冻结"))
                .andExpect(jsonPath("$.data.id").value(41))
                .andExpect(jsonPath("$.data.price").value(19.90))
                .andExpect(jsonPath("$.data.status").value("WAITING_MEET"));
    }

    @Test
    @DisplayName("业务异常返回真实 HTTP 状态码且不丢失细粒度业务错误码")
    void businessFailurePreservesDomainCode() throws Exception {
        when(tradingEntryService.createOrder(eq(7L), any(), anyString()))
                .thenThrow(new BusinessException(ResultCode.BALANCE_NOT_ENOUGH));

        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
                        .header(ApiHeaders.IDEMPOTENCY_KEY, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(3001))
                .andExpect(jsonPath("$.message").value("余额不足"))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"));
    }

    @Test
    @DisplayName("资金操作缺少或携带非法幂等键被统一拒绝（HTTP 400 + 业务码）")
    void malformedIdempotencyKeyIsRejected() throws Exception {
        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
                        .header(ApiHeaders.IDEMPOTENCY_KEY, "not-an-uuid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(3007))
                .andExpect(content().string(containsString("\"data\":null")));

        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        verify(tradingEntryService, never()).createOrder(anyLong(), any(), any());
    }

    @Test
    @DisplayName("管理员登录使用独立命名空间和最小身份响应")
    void adminLoginHasIndependentContract() throws Exception {
        AdminLoginVO response = new AdminLoginVO("admin.jwt.token",
                new AdminLoginVO.AdminUserVO(1L, "admin", "系统管理员", "ADMIN"));
        when(adminAuthService.login("admin", "123456")).thenReturn(response);

        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("admin.jwt.token"))
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.role").value("ADMIN"));
    }

    @Test
    @DisplayName("P0-1 认证矩阵：注销账户登录是业务拒绝 403+1008，而非 401")
    void cancelledAccountLoginIsBusiness403With1008() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ResultCode.USER_CANCELLED, "该账户已注销，注销后不可恢复"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolId":1,"studentId":"20260001","password":"123456"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(1008))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                        result.getResponse().getHeader("Set-Cookie"),
                        "403 业务拒绝不得清除会话 Cookie"));
    }

    @Test
    @DisplayName("P1-3：密码错误是业务 400（不触发前端登出）并携带处置元数据")
    void passwordErrorIsBusiness400Not401() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ResultCode.PASSWORD_ERROR, "学号或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolId":1,"studentId":"20260001","password":"wrong"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(1002))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"));
    }

    @Test
    @DisplayName("P1-3：来源未细分的交易繁忙 429 附 Retry-After，meta 保守标记结果不明")
    void tradeBusyReturns429WithRetryAfterAndUnknownMeta() throws Exception {
        when(tradingEntryService.createOrder(eq(7L), any(), anyString()))
                .thenThrow(new BusinessException(ResultCode.TRADE_BUSY));

        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
                        .header(ApiHeaders.IDEMPOTENCY_KEY, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(3004))
                .andExpect(jsonPath("$.meta.requestOutcome").value("UNKNOWN"))
                .andExpect(jsonPath("$.meta.retryAfterSeconds").doesNotExist())
                .andExpect(header().string("Retry-After", "2"));
    }

    @Test
    @DisplayName("P1-3：登录锁定 429 的 Retry-After 使用异常实例覆盖的动态秒数")
    void loginLockedRetryAfterUsesInstanceOverride() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ResultCode.LOGIN_LOCKED)
                        .withRetryAfterSeconds(137));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolId":1,"studentId":"20260001","password":"wrong"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1005))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"))
                .andExpect(header().string("Retry-After", "137"));
    }

    @Test
    @DisplayName("认证准入背压 429 附静态 Retry-After，meta REJECTED（闸门先于任何数据库访问）")
    void authBusyReturns429WithRetryAfterAndRejectedMeta() throws Exception {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ResultCode.AUTH_BUSY));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolId":1,"studentId":"20260001","password":"123456"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(1011))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"))
                .andExpect(header().string("Retry-After", "1"));
    }

    @Test
    @DisplayName("P1-3：系统错误 500 的 meta 标记结果不明（保留幂等键），无 Retry-After")
    void serverErrorMarksOutcomeUnknown() throws Exception {
        when(tradingEntryService.createOrder(eq(7L), any(), anyString()))
                .thenThrow(new RuntimeException("boom"));

        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
                        .header(ApiHeaders.IDEMPOTENCY_KEY, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.meta.requestOutcome").value("UNKNOWN"))
                .andExpect(header().doesNotExist("Retry-After"));
    }

    @Test
    @DisplayName("P4-7：strict 模式下抛出未声明业务码直接判定契约失败")
    void undeclaredBusinessCodeFailsContractInStrictMode() {
        when(authService.login(any()))
                .thenThrow(new BusinessException(ResultCode.ORDER_STATUS_ERROR));

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"schoolId":1,"studentId":"20260001","password":"123456"}
                                """)),
                "未声明业务码在 strict 模式下必须使契约测试失败");
    }

    @Test
    @DisplayName("P2-5：CORS 预检放行 X-Idempotency-Key，并暴露 Retry-After")
    void corsPreflightAllowsIdempotencyKeyAndExposesRetryAfter() throws Exception {
        mockMvc.perform(options("/api/order/create")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type,x-idempotency-key"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        java.util.Arrays.stream(
                                        result.getResponse().getHeaders("Access-Control-Allow-Headers").toArray(new String[0]))
                                .anyMatch(value -> value.toLowerCase().contains("x-idempotency-key")),
                        "预检必须放行 X-Idempotency-Key"))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertTrue(
                        java.util.Arrays.stream(
                                        result.getResponse().getHeaders("Access-Control-Expose-Headers").toArray(new String[0]))
                                .anyMatch(value -> value.toLowerCase().contains("retry-after")),
                        "Retry-After 必须对浏览器 JS 可见"));
    }

    @Test
    @DisplayName("P2-5：非白名单 Origin 的预检被拒绝")
    void corsPreflightRejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/order/create")
                        .header("Origin", "http://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("普通 409 幂等处理中（3006）返回 Retry-After=3 且不写 body 字段")
    void idempotencyProcessingCarriesHeaderOnly() throws Exception {
        when(tradingEntryService.createOrder(eq(7L), any(), anyString()))
                .thenThrow(new BusinessException(ResultCode.IDEMPOTENCY_PROCESSING));

        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
                        .header(ApiHeaders.IDEMPOTENCY_KEY, "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(3006))
                .andExpect(jsonPath("$.meta.requestOutcome").value("PROCESSING"))
                .andExpect(jsonPath("$.meta.retryAfterSeconds").doesNotExist())
                .andExpect(header().string("Retry-After", "3"));
    }

    @Test
    @DisplayName("multipart 上传契约：实际 multipart 请求能绑定 file part")
    void uploadImageBindsMultipartPart() throws Exception {
        byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 1, 2, 3};
        when(itemPublishService.uploadImage(any()))
                .thenReturn(new UploadImageVO("/uploads/items/20260826/abc.png"));

        mockMvc.perform(multipart("/api/item/upload-image")
                        .file(new MockMultipartFile("file", "a.png", "image/png", png)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("/uploads/items/20260826/abc.png"));

        org.mockito.ArgumentCaptor<MultipartFile> captor =
                org.mockito.ArgumentCaptor.forClass(MultipartFile.class);
        verify(itemPublishService).uploadImage(captor.capture());
        assertEquals("a.png", captor.getValue().getOriginalFilename());
        assertEquals(png.length, captor.getValue().getSize(), "multipart file 必须完整绑定");
    }

    @Test
    @DisplayName("multipart 上传契约：缺少 file part 返回 HTTP 400（统一失败信封）")
    void uploadImageMissingPartRejectedAsBadRequest() throws Exception {
        mockMvc.perform(multipart("/api/item/upload-image"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("file")))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"));

        verify(itemPublishService, never()).uploadImage(any());
    }

    @Test
    @DisplayName("multipart 上传契约：Content-Type 不匹配返回真实 HTTP 415（统一失败信封）")
    void uploadImageRejectsJsonWithUnsupportedMediaType() throws Exception {
        mockMvc.perform(post("/api/item/upload-image")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"file\":\"not-a-multipart-part\"}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value(415))
                .andExpect(jsonPath("$.message").value(containsString("格式")))
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"));

        verify(itemPublishService, never()).uploadImage(any());
    }

    @Test
    @DisplayName("MVC 传输契约：路径存在但方法不支持返回真实 HTTP 405（统一失败信封）")
    void existingPathRejectsUnsupportedMethod() throws Exception {
        // 不使用 /api/item/upload-image：GET 会被 /api/item/{id} 动态路由匹配并产生参数 400。
        mockMvc.perform(post("/api/auth/security-questions"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value(405))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    @DisplayName("MVC 传输契约：Accept 无法满足时返回真实 HTTP 406 与可解析 JSON 信封")
    void unacceptableResponseTypeKeepsJsonFailureEnvelope() throws Exception {
        mockMvc.perform(get("/api/auth/security-questions")
                        .accept(MediaType.APPLICATION_XML))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(406))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    @DisplayName("multipart 上传契约：实时 /v3/api-docs 生成 required multipart requestBody + binary file")
    void uploadImageContractInGeneratedSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andReturn();
        DocumentContext spec = JsonPath.parse(result.getResponse().getContentAsString());

        String upload = "$['paths']['/api/item/upload-image']['post']";
        assertEquals(true, spec.read(upload + "['requestBody']['required']", Boolean.class),
                "requestBody.required 必须为 true");
        assertAll("content 包含且仅包含 multipart/form-data",
                () -> assertEquals(1, spec.read(upload + "['requestBody']['content']", java.util.Map.class).size()),
                () -> assertTrue(spec.read(upload + "['requestBody']['content']", java.util.Map.class)
                        .containsKey("multipart/form-data")));
        java.util.List<?> required = spec.read(
                upload + "['requestBody']['content']['multipart/form-data']['schema']['required']", java.util.List.class);
        assertTrue(required.contains("file"), "schema.required 必须包含 file：" + required);
        assertEquals("string", spec.read(upload
                + "['requestBody']['content']['multipart/form-data']['schema']['properties']['file']['type']"));
        assertEquals("binary", spec.read(upload
                + "['requestBody']['content']['multipart/form-data']['schema']['properties']['file']['format']"));
    }

    @Test
    @DisplayName("strict 契约：跨校商品详情返回声明的 403（不构成契约违约）")
    void crossSchoolDetailReturnsDeclaredForbidden() throws Exception {
        when(marketplaceService.getDetail(eq(9L), eq(7L)))
                .thenThrow(new BusinessException(ResultCode.FORBIDDEN, "只能查看本校商品"));

        mockMvc.perform(get("/api/item/{id}", 9L).requestAttr("userId", 7L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"));
    }

    @Test
    @DisplayName("strict 契约：大厅 feed 用户不存在返回声明的 1006/404")
    void feedWithMissingUserReturnsDeclaredUserNotFound() throws Exception {
        when(marketplaceService.listFeed(any(), any(), any(), any(), anyString(), any(), any(), any(), anyInt(), eq(7L)))
                .thenThrow(new BusinessException(ResultCode.USER_NOT_FOUND));

        mockMvc.perform(get("/api/item/list").requestAttr("userId", 7L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(1006));
    }

    @Test
    @DisplayName("strict 契约：标签建议分类不存在返回声明的 404")
    void tagSuggestionsWithMissingCategoryReturnsDeclaredNotFound() throws Exception {
        when(itemPublishService.suggestTags(anyString(), any()))
                .thenThrow(new BusinessException(ResultCode.NOT_FOUND, "商品分类不存在"));

        mockMvc.perform(post("/api/item/tag-suggestions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"教材\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("strict 契约：重复举报返回声明的 409")
    void duplicateReportReturnsDeclaredConflict() throws Exception {
        org.mockito.Mockito.doThrow(new BusinessException(ResultCode.CONFLICT, "你已经举报过该商品"))
                .when(itemPublishService).report(eq(7L), eq(9L), any());

        mockMvc.perform(post("/api/item/{id}/reports", 9L)
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"PROHIBITED_ITEM\",\"details\":\"违禁品\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    @DisplayName("strict 契约：客服账号缺失返回声明的 500，非本人会话/跨校聊天返回声明的 403")
    void chatBusinessBranchesAreDeclared() throws Exception {
        when(chatService.startCustomerService(7L))
                .thenThrow(new BusinessException(ResultCode.SERVER_ERROR, "客服账号未配置"));
        mockMvc.perform(post("/api/chat/customer-service").requestAttr("userId", 7L))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.meta.requestOutcome").value("UNKNOWN"));

        when(chatService.startItemConversation(eq(7L), any()))
                .thenThrow(new BusinessException(ResultCode.FORBIDDEN, "只能与本校用户会话"));
        mockMvc.perform(post("/api/chat/start")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("strict 契约：跨校卖家档案返回声明的 403")
    void crossSchoolSellerDetailReturnsDeclaredForbidden() throws Exception {
        when(userService.getSellerDetail(eq(7L), eq(9L)))
                .thenThrow(new BusinessException(ResultCode.FORBIDDEN, "只能查看本校卖家资料"));

        mockMvc.perform(get("/api/user/{id}/seller-detail", 9L).requestAttr("userId", 7L))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.meta.requestOutcome").value("REJECTED"));
    }

    @Test
    @DisplayName("SSE 事件流：为当前登录用户建立 text/event-stream 异步连接并转发 event:chat")
    void chatStreamEstablishesEventStreamForCurrentUser() throws Exception {
        SseEmitter emitter = new SseEmitter(0L);
        when(chatEventBroadcaster.connect(7L)).thenReturn(emitter);

        MvcResult stream = mockMvc.perform(get("/api/chat/stream").requestAttr("userId", 7L))
                .andExpect(request().asyncStarted())
                .andReturn();

        emitter.send(SseEmitter.event().name("chat")
                .data(ChatEventVO.message("1_7", 10L, 1L)));
        emitter.complete();

        mockMvc.perform(asyncDispatch(stream))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(content().string(containsString("event:chat")))
                .andExpect(content().string(containsString("\"type\":\"MESSAGE\"")))
                .andExpect(content().string(containsString("\"conversationId\":\"1_7\"")));
        verify(chatEventBroadcaster).connect(7L);
    }
}
