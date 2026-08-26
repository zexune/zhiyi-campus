package com.zhiyi.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-3 序列化 fixture：区分必填值、显式 null、字段缺失与默认值，
 * 固化统一信封在 wire 上的精确 JSON 形状（保持 {code,message,data} 兼容）。
 */
class ApiEnvelopeSerializationTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    @DisplayName("成功信封：wire 形状保持 {code,message,data}，data 为显式 null")
    void successEnvelopeKeepsCompatibleShape() throws Exception {
        String json = mapper.writeValueAsString(ApiSuccess.ok());
        assertTrue(json.contains("\"code\":200"));
        assertTrue(json.contains("\"message\":\"操作成功\""));
        assertTrue(json.contains("\"data\":null"), "无数据成功必须显式 null：" + json);
    }

    @Test
    @DisplayName("成功信封携带数据：data 序列化为必填值")
    void successEnvelopeCarriesData() throws Exception {
        String json = mapper.writeValueAsString(ApiSuccess.ok(java.util.List.of(1, 2)));
        assertTrue(json.contains("\"data\":[1,2]"), json);
    }

    @Test
    @DisplayName("失败信封：明确拒绝携带 meta.requestOutcome=REJECTED")
    void rejectedFailureCarriesOutcomeMeta() throws Exception {
        String json = mapper.writeValueAsString(ApiFailure.of(ResultCode.BALANCE_NOT_ENOUGH));
        assertTrue(json.contains("\"code\":3001"));
        assertTrue(json.contains("\"meta\":{\"requestOutcome\":\"REJECTED\"}"),
                "处置元数据必须机器可读：" + json);
        assertTrue(json.contains("\"data\":null"), "无冲突详情时 data 为显式 null：" + json);
    }

    @Test
    @DisplayName("失败信封：处理中携带 meta.requestOutcome=PROCESSING")
    void processingFailureCarriesOutcomeMeta() throws Exception {
        String json = mapper.writeValueAsString(ApiFailure.of(ResultCode.IDEMPOTENCY_PROCESSING));
        assertTrue(json.contains("\"meta\":{\"requestOutcome\":\"PROCESSING\"}"), json);
    }

    @Test
    @DisplayName("失败信封：系统错误携带 meta.requestOutcome=UNKNOWN")
    void unknownFailureCarriesOutcomeMeta() throws Exception {
        String json = mapper.writeValueAsString(ApiFailure.of(ResultCode.SERVER_ERROR));
        assertTrue(json.contains("\"meta\":{\"requestOutcome\":\"UNKNOWN\"}"), json);
    }

    @Test
    @DisplayName("失败信封：冲突详情放在 data（显式值而非缺失）")
    void conflictDetailIsCarriedInData() throws Exception {
        String json = mapper.writeValueAsString(
                ApiFailure.of(ResultCode.PROFILE_CONFLICT, "资料已被修改", java.util.Map.of("nickname", "最新")));
        assertTrue(json.contains("\"data\":{\"nickname\":\"最新\"}"), json);
    }

    @Test
    @DisplayName("失败信封不变量：meta 缺失或 requestOutcome 为 null 直接拒绝创建")
    void failureEnvelopeRejectsMissingMeta() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ApiFailure(3001, "余额不足", null, null));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ApiFailure(3001, "余额不足", null,
                        new ApiFailure.FailureMeta(null)));
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new ApiFailure(200, "伪造成功", null,
                        new ApiFailure.FailureMeta(ResultCode.RequestOutcome.REJECTED)));
    }

    @Test
    @DisplayName("Retry-After 双层策略：实例覆盖值优先于 ResultCode 静态默认")
    void retryAfterInstanceOverrideWins() {
        assertEquals(300, new BusinessException(ResultCode.LOGIN_LOCKED).effectiveRetryAfterSeconds());
        assertEquals(137, new BusinessException(ResultCode.LOGIN_LOCKED).withRetryAfterSeconds(137)
                .effectiveRetryAfterSeconds());
        assertEquals(0, new BusinessException(ResultCode.BALANCE_NOT_ENOUGH).effectiveRetryAfterSeconds());
        // 非正覆盖值 clamp 为 1 秒，保证仍给出可退避信号
        assertEquals(1, new BusinessException(ResultCode.LOGIN_LOCKED).withRetryAfterSeconds(0)
                .effectiveRetryAfterSeconds());
    }

    @Test
    @DisplayName("requestOutcome 双层策略：实例执行事实优先于业务码保守默认值")
    void requestOutcomeInstanceOverrideWins() {
        BusinessException tradeBusy = new BusinessException(ResultCode.TRADE_BUSY);
        assertEquals(ResultCode.RequestOutcome.UNKNOWN, tradeBusy.effectiveRequestOutcome());
        assertEquals(ResultCode.RequestOutcome.REJECTED,
                tradeBusy.withRequestOutcome(ResultCode.RequestOutcome.REJECTED)
                        .effectiveRequestOutcome());
    }

    @Test
    @DisplayName("业务异常的实例 requestOutcome 覆盖值进入最终失败信封")
    void requestOutcomeInstanceOverrideReachesEnvelope() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                new AuthTokenCookieWriter("zhiyi_token", false, java.time.Duration.ofHours(24)),
                new BusinessErrorContractVerifier(false));
        org.springframework.mock.web.MockHttpServletRequest request =
                new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/order/create");
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        org.springframework.http.ResponseEntity<ApiFailure> entity = handler.handleBusinessException(
                new BusinessException(ResultCode.TRADE_BUSY)
                        .withRequestOutcome(ResultCode.RequestOutcome.REJECTED),
                request, response);

        assertEquals(ResultCode.RequestOutcome.REJECTED,
                entity.getBody().meta().requestOutcome());
    }

    @Test
    @DisplayName("拦截器直写路径保留业务异常的实例 requestOutcome 覆盖值")
    void webResponseUtilPreservesRequestOutcomeInstanceOverride() throws Exception {
        WebResponseUtil writer = new WebResponseUtil(JsonMapper.builder().build());
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        writer.writeFailure(response,
                new BusinessException(ResultCode.TRADE_BUSY)
                        .withRequestOutcome(ResultCode.RequestOutcome.REJECTED));

        assertEquals(429, response.getStatus());
        assertEquals("2", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains(
                "\"meta\":{\"requestOutcome\":\"REJECTED\"}"),
                response.getContentAsString());
    }

    @Test
    @DisplayName("兜底规则：最终 HTTP 401 无论异常来源都清除会话 Cookie")
    void any401ClearsSessionCookie() {
        AuthTokenCookieWriter cookieWriter = new AuthTokenCookieWriter("zhiyi_token", false, java.time.Duration.ofHours(24));
        GlobalExceptionHandler handler = new GlobalExceptionHandler(cookieWriter, new BusinessErrorContractVerifier(false));
        org.springframework.mock.web.MockHttpServletResponse response = new org.springframework.mock.web.MockHttpServletResponse();
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/whatever");

        handler.handleBusinessException(new BusinessException(ResultCode.UNAUTHORIZED), request, response);

        assertTrue(response.getHeader("Set-Cookie") != null && response.getHeader("Set-Cookie").contains("Max-Age=0"),
                "401 必须清除会话 Cookie");
        assertTrue(response.getHeader("Retry-After") == null, "401 无退避建议");
    }

    @Test
    @DisplayName("Servlet 上传限额拒绝保留 HTTP 413 并返回完整失败信封")
    void uploadLimitFailureKeeps413Envelope() {
        AuthTokenCookieWriter cookieWriter = new AuthTokenCookieWriter(
                "zhiyi_token", false, java.time.Duration.ofHours(24));
        GlobalExceptionHandler handler = new GlobalExceptionHandler(
                cookieWriter, new BusinessErrorContractVerifier(false));
        org.springframework.mock.web.MockHttpServletResponse response =
                new org.springframework.mock.web.MockHttpServletResponse();

        org.springframework.http.ResponseEntity<ApiFailure> entity = handler.handleUploadTooLarge(
                new MaxUploadSizeExceededException(5 * 1024 * 1024L), response);

        assertEquals(413, entity.getStatusCode().value());
        assertEquals(413, entity.getBody().code());
        assertEquals(ResultCode.RequestOutcome.REJECTED,
                entity.getBody().meta().requestOutcome());
        assertEquals(null, entity.getBody().data());
    }
}
