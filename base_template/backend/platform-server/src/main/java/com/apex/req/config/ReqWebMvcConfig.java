package com.apex.req.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * 需求管理模块静态沙盒原型资源映射配置
 */
@Configuration
public class ReqWebMvcConfig implements WebMvcConfigurer {

    @Value("${storage.prototype.path:./storage/prototypes}")
    private String prototypeStorageRoot;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        File dir = new File(prototypeStorageRoot);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String absolutePath = dir.getAbsolutePath().replace('\\', '/');
        if (!absolutePath.endsWith("/")) {
            absolutePath += "/";
        }
        registry.addResourceHandler("/static/prototypes/**")
                .addResourceLocations("file:" + absolutePath);
    }
}
