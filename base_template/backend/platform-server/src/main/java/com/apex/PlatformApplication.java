package com.apex;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 个人多模块学习平台 (Learning Platform) 统一聚合主启动类
 */
@SpringBootApplication(scanBasePackages = {"com.apex"})
@MapperScan({"com.apex.sys.mapper", "com.apex.network.mapper"})
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
        System.out.println("====================================================");
        System.out.println("🚀 个人多模块学习平台 (Learning Platform) 启动成功！");
        System.out.println("📡 聚合子模块: platform-common, module-network-sim");
        System.out.println("====================================================");
    }
}
