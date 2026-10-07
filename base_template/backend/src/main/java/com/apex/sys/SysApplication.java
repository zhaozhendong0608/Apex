package com.apex.sys;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 🚀 SysPlatform 通用管理平台 - Spring Boot 主启动类
 */
@SpringBootApplication
@MapperScan("com.apex.sys.mapper")
public class SysApplication {

    public static void main(String[] args) {
        SpringApplication.run(SysApplication.class, args);
        System.out.println("=================================================");
        System.out.println("🟢 SysPlatform Spring Boot 3 服务已成功启动！");
        System.out.println("🌐 端口: 8080");
        System.out.println("=================================================");
    }
}
