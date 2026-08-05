package com.zhiyi.utils;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwtUtilsTest {

    @Test
    void generatedTokenContainsVersion() {
        JwtUtils jwtUtils = new JwtUtils(
                "01234567890123456789012345678901", 60_000);

        Claims claims = jwtUtils.parse(
                jwtUtils.generateToken(42L, "USER", 3));

        assertEquals(3,
                claims.get(JwtUtils.TOKEN_VERSION_CLAIM, Integer.class));
    }
}
