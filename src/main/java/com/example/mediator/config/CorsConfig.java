package com.example.mediator.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // 適用所有 API
                .allowedOriginPatterns("*") // 接受所有來源，或自己設定白名單
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // 建議補上 OPTIONS
                .allowedHeaders("*") // 允許任何標頭
                .exposedHeaders("*") // 前端可以取的標頭
                .allowCredentials(true) // 允許攜帶 cookie
                .maxAge(3600); // 預檢請求有效時間（秒）
    }
}
