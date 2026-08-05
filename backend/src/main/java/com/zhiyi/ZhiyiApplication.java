package com.zhiyi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.zhiyi.module.**.mapper")
public class ZhiyiApplication {
    public static void main(String[] args) {
        SpringApplication.run(ZhiyiApplication.class, args);
        System.out.println("🎓 智易校园启动成功！http://localhost:8080");
    }
}
