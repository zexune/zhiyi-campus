package com.zhiyi.web;

import com.zhiyi.common.BusinessException;
import com.zhiyi.common.AuthTokenCookieWriter;
import com.zhiyi.common.GlobalExceptionHandler;
import com.zhiyi.common.ResultCode;
import com.zhiyi.config.WebMvcConfig;
import com.zhiyi.interceptor.JwtInterceptor;
import com.zhiyi.interceptor.RoleInterceptor;
import com.zhiyi.module.admin.controller.AdminAuthController;
import com.zhiyi.module.admin.service.AdminAuthService;
import com.zhiyi.module.admin.vo.AdminLoginVO;
import com.zhiyi.module.trade.controller.OrderController;
import com.zhiyi.module.trade.service.OrderQueryService;
import com.zhiyi.module.trade.service.OrderService;
import com.zhiyi.module.trade.service.ReviewService;
import com.zhiyi.module.trade.vo.OrderVO;
import com.zhiyi.module.user.controller.AuthController;
import com.zhiyi.module.user.service.AccountSecurityService;
import com.zhiyi.module.user.service.AuthService;
import com.zhiyi.module.user.vo.LoginVO;
import com.zhiyi.module.user.vo.UserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web MVC 切片契约测试：覆盖路由、JSON 序列化、Bean Validation 与统一异常响应。
 * 业务分支仍由纯单元测试负责，避免把控制器测试写成脆弱的大型上下文测试。
 */
@WebMvcTest
@ContextConfiguration(classes = ApiControllerContractTest.MvcTestConfiguration.class)
@TestPropertySource(properties = "zhiyi.cors.allowed-origins=http://localhost:3000")
class ApiControllerContractTest {

    @Configuration(proxyBeanMethods = false)
    @Import({
            AuthController.class,
            OrderController.class,
            AdminAuthController.class,
            GlobalExceptionHandler.class,
            WebMvcConfig.class,
            AuthTokenCookieWriter.class
    })
    static class MvcTestConfiguration {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private OrderService orderService;
    @MockitoBean private OrderQueryService orderQueryService;
    @MockitoBean private ReviewService reviewService;
    @MockitoBean private AdminAuthService adminAuthService;
    @MockitoBean private AccountSecurityService accountSecurityService;
    @MockitoBean private JwtInterceptor jwtInterceptor;
    @MockitoBean private RoleInterceptor roleInterceptor;

    @BeforeEach
    void allowRequestsThroughAlreadyUnitTestedInterceptors() throws Exception {
        when(jwtInterceptor.preHandle(any(), any(), any())).thenReturn(true);
        when(roleInterceptor.preHandle(any(), any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("注册参数错误保持统一响应契约，且不会进入业务层")
    void registrationValidationUsesUnifiedEnvelope() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("studentId")))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("畸形 JSON 请求体映射为 400 而非兜底 500")
    void malformedJsonMapsToBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"schoolId\": 1, "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.data").doesNotExist());

        verify(authService, never()).login(any());
    }

    @Test
    @DisplayName("查询参数类型不匹配映射为 400 而非兜底 500")
    void requestParamTypeMismatchMapsToBadRequest() throws Exception {
        mockMvc.perform(get("/api/order/my-bought")
                        .requestAttr("userId", 7L)
                        .param("page", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("page")))
                .andExpect(jsonPath("$.data").doesNotExist());

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
        when(orderService.createOrder(eq(7L), any())).thenReturn(order);

        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
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
    @DisplayName("业务异常不会退化成 500 或丢失业务错误码")
    void businessFailurePreservesDomainCode() throws Exception {
        when(orderService.createOrder(eq(7L), any()))
                .thenThrow(new BusinessException(ResultCode.BALANCE_NOT_ENOUGH));

        mockMvc.perform(post("/api/order/create")
                        .requestAttr("userId", 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemId\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(3001))
                .andExpect(jsonPath("$.message").value("余额不足"))
                .andExpect(jsonPath("$.data").doesNotExist());
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
}
