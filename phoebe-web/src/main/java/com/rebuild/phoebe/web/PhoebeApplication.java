package com.rebuild.phoebe.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Phoebe 重建版启动类。
 * <p>
 * 声明：本项目根据公开资料重建知识营销 CPC 业务端形态，不是原 Phoebe 项目源码。
 */
@SpringBootApplication(scanBasePackages = "com.rebuild.phoebe")
@MapperScan("com.rebuild.phoebe.domain.mapper")
public class PhoebeApplication {
    public static void main(String[] args) {
        SpringApplication.run(PhoebeApplication.class, args);
    }
}
