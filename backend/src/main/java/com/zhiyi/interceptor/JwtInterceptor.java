package com.zhiyi.interceptor;

import com.zhiyi.common.WebResponseUtil;
import com.zhiyi.common.enums.UserStatus;
import com.zhiyi.module.user.support.UserAuthState;
import com.zhiyi.module.user.support.UserStateCache;
import com.zhiyi.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * JWT 登录拦截器 —— 校验每个请求的 Token，把 userId 和 role 放入 Request 供后续 Controller 使用。
 *
 * 高并发设计：
 * - Token 只解析一次（一次签名验证拿到全部 Claims）；
 * - 封禁状态 / Token 版本走 Caffeine 本地缓存（UserStateCache），不逐请求查库；
 * - 重置密码、改密、封禁或注销后推进版本，旧 Token 立刻作废（需求 1.3/1.6）。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    private static final Pattern PUBLIC_USER_CARD = Pattern.compile("^/api/user/\\d+/card$");
    private static final Pattern PUBLIC_USER_REPUTATION = Pattern.compile("^/api/user/\\d+/reputation$");
    private static final int MAX_TOKEN_LENGTH = 4096;

    private final JwtUtils jwtUtils;
    private final UserStateCache userStateCache;

    public JwtInterceptor(JwtUtils jwtUtils, UserStateCache userStateCache) {
        this.jwtUtils = jwtUtils;
        this.userStateCache = userStateCache;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) throws Exception {
        // OPTIONS 预检请求直接放行
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 动态公开接口必须同时匹配 HTTP 方法和完整路径，避免 PUT/DELETE 商品接口被一并放行。
        if (isDynamicPublicGet(request)) {
            return true;
        }

        String token = request.getHeader("Authorization");
        if (token == null || !token.startsWith("Bearer ")) {
            WebResponseUtil.writeJson(response, 401, 401, "未登录");
            return false;
        }

        String encodedToken = token.substring(7);
        if (encodedToken.isBlank() || encodedToken.length() > MAX_TOKEN_LENGTH) {
            WebResponseUtil.writeJson(response, 401, 401, "Token 格式无效");
            return false;
        }

        Claims claims = jwtUtils.parse(encodedToken); // 单次验签并取得全部 Claims
        if (claims == null) {
            WebResponseUtil.writeJson(response, 401, 401, "Token 无效或已过期");
            return false;
        }

        Long userId;
        try {
            userId = Long.parseLong(claims.getSubject());
        } catch (NumberFormatException exception) {
            WebResponseUtil.writeJson(response, 401, 401, "Token 格式无效");
            return false;
        }
        UserAuthState state = userStateCache.get(userId);
        if (state == null) {
            WebResponseUtil.writeJson(response, 401, 401, "用户不存在");
            return false;
        }

        Integer claimVersion = jwtUtils.getTokenVersion(claims);
        int issuedVersion = claimVersion == null ? 0 : claimVersion;
        if (claimVersion == null) {
            WebResponseUtil.writeJson(response, 401, 401, "登录状态已失效，请重新登录");
            return false;
        }
        int currentVersion = state.tokenVersion() == null ? 0 : state.tokenVersion();
        String issuedRole = claims.get(JwtUtils.ROLE_CLAIM, String.class);
        if (issuedVersion != currentVersion || !Objects.equals(issuedRole, state.role().code())) {
            WebResponseUtil.writeJson(response, 401, 401, "登录状态已失效，请重新登录");
            return false;
        }

        // 封禁/注销校验：永久封禁与已注销直接拒绝；临时封禁未到期拒绝（到期由登录流程恢复 ACTIVE）
        if (state.status() == UserStatus.CANCELLED) {
            WebResponseUtil.writeJson(response, 401, 1008, "该账户已注销");
            return false;
        }
        if (state.status() == UserStatus.BANNED_PERM) {
            WebResponseUtil.writeJson(response, 403, 1003, "该账户已被永久封禁");
            return false;
        }
        if (state.status() == UserStatus.BANNED_TEMP
                && state.banUntilTime() != null
                && state.banUntilTime().isAfter(LocalDateTime.now())) {
            WebResponseUtil.writeJson(response, 403, 1003, "账户已被封禁");
            return false;
        }

        // 把 userId 和 role 放入 request attribute，Controller 里直接取
        request.setAttribute("userId", userId);
        request.setAttribute("role", state.role().code());
        return true;
    }

    private boolean isDynamicPublicGet(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String path = contextPath.isEmpty() ? requestUri : requestUri.substring(contextPath.length());
        return PUBLIC_USER_CARD.matcher(path).matches()
                || PUBLIC_USER_REPUTATION.matcher(path).matches();
    }
}
