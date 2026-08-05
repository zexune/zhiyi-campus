package com.zhiyi.interceptor;

import com.zhiyi.common.annotation.RoleRequired;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleInterceptorTest {

    private final RoleInterceptor interceptor = new RoleInterceptor();

    private static class ProtectedController {
        @RoleRequired("ADMIN")
        public void protectedAction() {
        }

        public void publicAction() {
        }
    }

    @Test
    void adminPassesAndUserReceives403() throws Exception {
        HandlerMethod handler = new HandlerMethod(
                new ProtectedController(),
                ProtectedController.class.getDeclaredMethod("protectedAction"));

        MockHttpServletRequest admin = new MockHttpServletRequest();
        admin.setAttribute("role", "ADMIN");
        assertTrue(interceptor.preHandle(
                admin, new MockHttpServletResponse(), handler));

        MockHttpServletRequest user = new MockHttpServletRequest();
        user.setAttribute("role", "USER");
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(user, response, handler));
        assertEquals(403, response.getStatus());
    }

    @Test
    void unannotatedMethodDoesNotRequireRole() throws Exception {
        HandlerMethod handler = new HandlerMethod(
                new ProtectedController(),
                ProtectedController.class.getDeclaredMethod("publicAction"));

        assertTrue(interceptor.preHandle(
                new MockHttpServletRequest(),
                new MockHttpServletResponse(),
                handler));
    }
}
