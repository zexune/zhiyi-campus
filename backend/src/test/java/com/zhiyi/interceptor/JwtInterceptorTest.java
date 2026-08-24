package com.zhiyi.interceptor;

import com.zhiyi.common.AuthTokenCookieWriter;
import com.zhiyi.common.enums.UserRole;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.user.entity.SysUser;
import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.utils.JwtUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JwtInterceptor 测试 —— 适配 v3.1 并发重构：
 * UserStateCache/UserAuthState 已删除，鉴权状态改为每请求 SysUserMapper.selectAuthState 主库直读，
 * 只放行 ACTIVE（BANNED_TEMP 一律 403 1003）。
 */
class JwtInterceptorTest {

    private static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    private final AuthTokenCookieWriter cookieWriter = new AuthTokenCookieWriter("zhiyi_token", false, Duration.ofHours(24));

    private final JwtInterceptor interceptor = new JwtInterceptor(null, null, cookieWriter);

    @Test
    void publicUserRoutesRemainPublic() throws Exception {
        MockHttpServletResponse cardResponse = new MockHttpServletResponse();
        MockHttpServletResponse reputationResponse = new MockHttpServletResponse();

        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/user/42/card"), cardResponse, new Object()));
        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/user/42/reputation"), reputationResponse, new Object()));
    }

    @Test
    void itemDetailRequiresAuthentication() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/item/42"), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void authenticatedItemDetailReceivesUserId() throws Exception {
        JwtUtils utils = jwtUtils();
        JwtInterceptor secured = securedInterceptor(
                utils, authState(42L, UserRole.USER, UserStatus.ACTIVE, 3));
        MockHttpServletRequest request =
                authenticatedRequest("/api/item/99", utils.generateToken(42L, "USER", 3));

        assertTrue(secured.preHandle(
                request, new MockHttpServletResponse(), new Object()));
        assertEquals(42L, request.getAttribute("userId"));
    }

    @Test
    void nonGetItemRoutesStillRequireAuthentication() throws Exception {
        MockHttpServletResponse putResponse = new MockHttpServletResponse();
        MockHttpServletResponse deleteResponse = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("PUT", "/api/item/42"), putResponse, new Object()));
        assertEquals(401, putResponse.getStatus());
        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("DELETE", "/api/item/42"), deleteResponse, new Object()));
        assertEquals(401, deleteResponse.getStatus());
    }

    @Test
    void itemSubRoutesAreNotTreatedAsPublicDetails() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/item/42/favorite"), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void sellerContactDetailRequiresAuthentication() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(interceptor.preHandle(
                new MockHttpServletRequest("GET", "/api/user/42/seller-detail"), response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void matchingVersionIsAccepted() throws Exception {
        JwtUtils utils = jwtUtils();
        JwtInterceptor secured = securedInterceptor(
                utils, authState(42L, UserRole.USER, UserStatus.ACTIVE, 3));
        MockHttpServletRequest request = authenticatedRequest(
                utils.generateToken(42L, "USER", 3));

        assertTrue(secured.preHandle(
                request, new MockHttpServletResponse(), new Object()));
        assertEquals(42L, request.getAttribute("userId"));
        assertEquals("USER", request.getAttribute("role"));
    }

    @Test
    void mismatchedVersionIsRejected() throws Exception {
        JwtUtils utils = jwtUtils();
        JwtInterceptor secured = securedInterceptor(
                utils, authState(42L, UserRole.USER, UserStatus.ACTIVE, 4));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(secured.preHandle(
                authenticatedRequest(utils.generateToken(42L, "USER", 3)),
                response,
                new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void mismatchedRoleClaimIsRejected() throws Exception {
        JwtUtils utils = jwtUtils();
        // Token 声明 ADMIN，数据库当前角色是 USER：改密/升级角色后旧 Token 必须失效
        JwtInterceptor secured = securedInterceptor(
                utils, authState(42L, UserRole.USER, UserStatus.ACTIVE, 3));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(secured.preHandle(
                authenticatedRequest(utils.generateToken(42L, "ADMIN", 3)),
                response,
                new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void bannedTempUserIsRejectedWith1003EvenIfBanExpired() throws Exception {
        JwtUtils utils = jwtUtils();
        // 到期恢复只能由登录事务完成：请求路径不做时间比较，一律拒绝
        JwtInterceptor secured = securedInterceptor(
                utils, authState(42L, UserRole.USER, UserStatus.BANNED_TEMP, 3));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(secured.preHandle(
                authenticatedRequest(utils.generateToken(42L, "USER", 3)),
                response,
                new Object()));
        assertEquals(403, response.getStatus());
        assertTrue(response.getContentAsString().contains("1003"));
    }

    @Test
    void cancelledUserIsRejectedWith1008() throws Exception {
        JwtUtils utils = jwtUtils();
        JwtInterceptor secured = securedInterceptor(
                utils, authState(42L, UserRole.USER, UserStatus.CANCELLED, 3));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(secured.preHandle(
                authenticatedRequest(utils.generateToken(42L, "USER", 3)),
                response,
                new Object()));
        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("1008"));
    }

    @Test
    void missingAuthStateRowIsRejected() throws Exception {
        JwtUtils utils = jwtUtils();
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectAuthState(42L)).thenReturn(null);
        JwtInterceptor secured = new JwtInterceptor(utils, userMapper, cookieWriter);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(secured.preHandle(
                authenticatedRequest(utils.generateToken(42L, "USER", 3)),
                response,
                new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void sessionCookieAuthenticatesWhenBearerHeaderAbsent() throws Exception {
        JwtUtils utils = jwtUtils();
        JwtInterceptor secured = securedInterceptor(
                utils, authState(42L, UserRole.USER, UserStatus.ACTIVE, 3));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/item/99");
        request.setCookies(new Cookie("zhiyi_token", utils.generateToken(42L, "USER", 3)));

        assertTrue(secured.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(42L, request.getAttribute("userId"));
    }

    @Test
    void blankCookieValueIsRejectedLikeMissingCredentials() throws Exception {
        JwtInterceptor secured = securedInterceptor(
                jwtUtils(), authState(42L, UserRole.USER, UserStatus.ACTIVE, 3));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/item/99");
        request.setCookies(new Cookie("zhiyi_token", ""));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(secured.preHandle(request, response, new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void bearerHeaderTakesPrecedenceOverCookie() throws Exception {
        JwtUtils utils = jwtUtils();
        JwtInterceptor secured = securedInterceptor(
                utils, authState(42L, UserRole.USER, UserStatus.ACTIVE, 3));
        String validToken = utils.generateToken(42L, "USER", 3);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/item/99");
        request.addHeader("Authorization", "Bearer " + validToken);
        // Cookie 值是无效 Token，但不应被读取
        request.setCookies(new Cookie("zhiyi_token", "not-a-jwt"));

        assertTrue(secured.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals(42L, request.getAttribute("userId"));
    }

    private JwtInterceptor securedInterceptor(JwtUtils utils, SysUser authState) {
        SysUserMapper userMapper = mock(SysUserMapper.class);
        when(userMapper.selectAuthState(authState.getId())).thenReturn(authState);
        return new JwtInterceptor(utils, userMapper, cookieWriter);
    }

    private SysUser authState(Long id, UserRole role, UserStatus status, int tokenVersion) {
        SysUser state = new SysUser();
        state.setId(id);
        state.setRole(role);
        state.setStatus(status);
        state.setTokenVersion(tokenVersion);
        state.setIsSystem(false);
        return state;
    }

    private JwtUtils jwtUtils() {
        return new JwtUtils(SECRET, Duration.ofMinutes(1));
    }

    private MockHttpServletRequest authenticatedRequest(String token) {
        return authenticatedRequest("/api/user/profile", token);
    }

    private MockHttpServletRequest authenticatedRequest(String path, String token) {
        MockHttpServletRequest request =
                new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
