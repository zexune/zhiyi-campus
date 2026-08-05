package com.zhiyi;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 测试用户 BCrypt 密码哈希生成器
 * 运行方式：在 backend 目录执行
 *   mvn exec:java -Dexec.mainClass="com.zhiyi.BcryptUtil" -q
 * 或者编译后直接运行 class 文件
 */
public class BcryptUtil {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String[] passwords = {"admin", "seller1", "buyer1", "seller2", "buyer2",
                "banned_temp", "banned_perm", "cancelled", "edge_user", "high_level"};

        System.out.println("-- BCrypt 密码哈希（复制到 SQL）--");
        for (String pw : passwords) {
            System.out.printf("%-15s → %s%n", pw, encoder.encode(pw));
        }
    }
}
