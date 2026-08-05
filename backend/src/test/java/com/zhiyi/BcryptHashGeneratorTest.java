package com.zhiyi;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class BcryptHashGeneratorTest {

    @Test
    public void generateHashes() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String[] passwords = {"123456"};

        System.out.println("\n===== BCrypt 哈希（复制到 SQL）=====");
        for (String pw : passwords) {
            System.out.printf("密码: %s → %s%n", pw, encoder.encode(pw));
        }
        System.out.println("======================================\n");
    }
}
