package com.zhiyi.interceptor;

import com.zhiyi.module.user.mapper.SysUserMapper;
import com.zhiyi.module.user.support.UserAuthState;
import com.zhiyi.module.user.support.UserStateCache;
import com.zhiyi.utils.JwtUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class JwtInterceptorTest {

    private static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    private final JwtInterceptor interceptor = new JwtInterceptor(null, null);

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
        JwtInterceptor secured = new JwtInterceptor(
                utils,
                new FixedUserStateCache(
                        new UserAuthState(42L, "USER", "ACTIVE", null, 3)));
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
        JwtInterceptor secured = new JwtInterceptor(
                utils,
                new FixedUserStateCache(
                        new UserAuthState(42L, "USER", "ACTIVE", null, 3)));
        MockHttpServletRequest request = authenticatedRequest(
                utils.generateToken(42L, "USER", 3));

        assertTrue(secured.preHandle(
                request, new MockHttpServletResponse(), new Object()));
        assertEquals(42L, request.getAttribute("userId"));
    }

    @Test
    void mismatchedVersionIsRejected() throws Exception {
        JwtUtils utils = jwtUtils();
        JwtInterceptor secured = new JwtInterceptor(
                utils,
                new FixedUserStateCache(
                        new UserAuthState(42L, "USER", "ACTIVE", null, 4)));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertFalse(secured.preHandle(
                authenticatedRequest(utils.generateToken(42L, "USER", 3)),
                response,
                new Object()));
        assertEquals(401, response.getStatus());
    }

    @Test
    void nullVersionIsNormalizedToInitialVersion() throws Exception {
        JwtInterceptor secured = new JwtInterceptor(
                jwtUtils(),
                new FixedUserStateCache(
                        new UserAuthState(42L, "USER", "ACTIVE", null, 0)));

        String token = jwtUtils().generateToken(42L, "USER", null);

        assertTrue(secured.preHandle(authenticatedRequest(token), new MockHttpServletResponse(), new Object()));
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

    private static final class FixedUserStateCache extends UserStateCache {
        private final UserAuthState state;

        private FixedUserStateCache(UserAuthState state) {
            super(mock(SysUserMapper.class), 60);
            this.state = state;
        }

        @Override
        public UserAuthState get(Long userId) {
            return state;
        }
    }
}
