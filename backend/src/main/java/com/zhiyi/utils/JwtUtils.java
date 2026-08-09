package com.zhiyi.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 基于 JJWT 0.12 的不可变 JWT 签发与校验组件。
 */
@Component
public final class JwtUtils {

    public static final String TOKEN_VERSION_CLAIM = "tokenVersion";
    public static final String ROLE_CLAIM = "role";

    private static final String ISSUER = "zhiyi-campus";
    private static final String AUDIENCE = "zhiyi-campus-web";

    private final SecretKey key;
    private final Duration expiration;
    private final JwtParser parser;

    public JwtUtils(@Value("${zhiyi.jwt.secret}") String base64Secret,
                    @Value("${zhiyi.jwt.expiration}") Duration expiration) {
        if (base64Secret == null || base64Secret.isBlank()) {
            throw new IllegalArgumentException("JWT_SECRET must be a Base64 encoded 256-bit secret.");
        }
        byte[] secretBytes;
        try {
            secretBytes = Decoders.BASE64.decode(base64Secret);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("JWT_SECRET must be valid Base64.", exception);
        }
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must decode to at least 32 bytes.");
        }
        if (expiration == null || expiration.isZero() || expiration.isNegative()) {
            throw new IllegalArgumentException("JWT expiration must be positive.");
        }

        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.expiration = expiration;
        this.parser = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(ISSUER)
                .requireAudience(AUDIENCE)
                .build();
    }

    public String generateToken(Long userId, String role, Integer tokenVersion) {
        int version = tokenVersion == null ? 0 : tokenVersion;
        if (version < 0) {
            throw new IllegalArgumentException("Token version must not be negative.");
        }
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .audience().add(AUDIENCE).and()
                .subject(String.valueOf(userId))
                .claim(ROLE_CLAIM, role)
                .claim(TOKEN_VERSION_CLAIM, version)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    public Long getUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    public String getRole(String token) {
        return parseClaims(token).get(ROLE_CLAIM, String.class);
    }

    /**
     * JSON 适配器可能用不同的 {@link Number} 子类承载整数，统一做无损转换和范围校验。
     */
    public Integer getTokenVersion(Claims claims) {
        Object rawVersion = claims.get(TOKEN_VERSION_CLAIM);
        if (!(rawVersion instanceof Number number)) {
            return null;
        }
        double decimalValue = number.doubleValue();
        int integerValue = number.intValue();
        if (!Double.isFinite(decimalValue) || decimalValue != integerValue || integerValue < 0) {
            return null;
        }
        return integerValue;
    }

    public boolean validate(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * 单次验签并返回全部 Claims；任何格式、签名或时效异常都统一返回 {@code null}。
     */
    public Claims parse(String token) {
        try {
            return parseClaims(token);
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }

    private Claims parseClaims(String token) {
        return parser.parseSignedClaims(token).getPayload();
    }
}
