package com.zhiyi.common;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 登录凭证 Cookie 读写 —— JWT 通过 httpOnly Cookie 下发，前端 JavaScript 无法读取，
 * 消除 XSS 窃取 Token 的攻击面；Authorization: Bearer 仍保留（Swagger / 编程客户端用）。
 *
 * CSRF 评估：Cookie 限定 Path=/api 且 SameSite=Lax（跨站 POST 不携带），
 * 接口仅接受 JSON 请求体（表单无法伪造 Content-Type），跨源再受 CORS 白名单拦截，无需额外 Token。
 */
@Component
public class AuthTokenCookieWriter {

    private final String cookieName;
    private final boolean secure;
    private final Duration maxAge;

    public AuthTokenCookieWriter(
            @Value("${zhiyi.auth.cookie-name:zhiyi_token}") String cookieName,
            @Value("${zhiyi.auth.cookie-secure:false}") boolean secure,
            @Value("${zhiyi.jwt.expiration:24h}") Duration maxAge) {
        this.cookieName = cookieName;
        this.secure = secure;
        this.maxAge = maxAge;
    }

    /** 登录/注册成功后写入会话 Cookie，有效期与 JWT 本体一致。 */
    public void write(HttpServletResponse response, String token) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseBuilder()
                .maxAge(maxAge)
                .value(token)
                .build()
                .toString());
    }

    /** 登出时立即失效（Max-Age=0 覆盖浏览器内已有 Cookie）。 */
    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, baseBuilder()
                .maxAge(Duration.ZERO)
                .value("")
                .build()
                .toString());
    }

    /** 读取请求携带的会话 Cookie；不存在返回 null。 */
    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private ResponseCookie.ResponseCookieBuilder baseBuilder() {
        return ResponseCookie.from(cookieName)
                .httpOnly(true)
                .secure(secure)
                .path("/api")
                .sameSite("Lax");
    }
}
