package com.example.api_mediator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 適用於所有路徑
                .allowedOriginPatterns("http://localhost:[*]") // 允許的來源
                .allowedMethods("GET", "POST", "PUT", "DELETE") // 允許的 HTTP 方法
                .allowedHeaders("*") // 允許所有請求頭
                .exposedHeaders("*") // 暴露 Content-Disposition 給前端
//                .exposedHeaders("Content-Disposition") // 暴露 Content-Disposition 給前端
                .allowCredentials(true) // 是否允許攜帶憑證（如 Cookie）
                .maxAge(3600); // 預檢請求的有效期（秒）
    }
}