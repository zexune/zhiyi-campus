package com.zhiyi.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类 —— 生成 Token、解析 Token、校验 Token
 */
@Component
public class JwtUtils {

    public static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    private final SecretKey key;
    private final long expiration;

    public JwtUtils(@Value("${zhiyi.jwt.secret}") String secret,
                    @Value("${zhiyi.jwt.expiration}") long expiration) {
        byte[] secretBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be set and contain at least 32 UTF-8 bytes.");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = expiration;
    }

    /**
     * 生成 Token
     */
    public String generateToken(Long userId, String role, Integer tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .claim(TOKEN_VERSION_CLAIM, tokenVersion == null ? 0 : tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .signWith(key)
                .compact();
    }


    /**
     * 从 Token 中解析用户 ID
     */
    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /**
     * 从 Token 中解析角色
     */
    public String getRole(String token) {
        return parseClaims(token).get("role", String.class);
    }

    /**
     * 校验 Token 是否有效
     */
    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * 解析 Token；无效/过期返回 null。
     * 高并发提示：拦截器用它一次解析拿到全部 Claims（userId/role/iat），
     * 避免 validate + getUserId + getRole 三次重复的签名验证开销。
     */
    public Claims parse(String token) {
        try {
            return parseClaims(token);
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
