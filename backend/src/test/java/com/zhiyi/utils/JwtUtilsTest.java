package com.zhiyi.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwtUtilsTest {

    private static final String SECRET = "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE=";

    @Test
    void generatedTokenContainsVersion() {
        JwtUtils jwtUtils = new JwtUtils(
                SECRET, Duration.ofMinutes(1));

        Claims claims = jwtUtils.parse(
                jwtUtils.generateToken(42L, "USER", 3));

        assertEquals(3,
                jwtUtils.getTokenVersion(claims));
    }

    @Test
    void tokenFromPreviousFormatIsRejected() {
        JwtUtils jwtUtils = new JwtUtils(SECRET, Duration.ofMinutes(1));
        Instant now = Instant.now();
        String previousToken = Jwts.builder()
                .subject("42")
                .claim(JwtUtils.ROLE_CLAIM, "USER")
                .claim(JwtUtils.TOKEN_VERSION_CLAIM, 0)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), Jwts.SIG.HS256)
                .compact();

        assertNull(jwtUtils.parse(previousToken));
    }
}
