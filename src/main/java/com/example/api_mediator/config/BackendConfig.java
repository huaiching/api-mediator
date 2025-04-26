package com.example.api_mediator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "proxy")
public class BackendConfig {

    /**
     * List of backend APIs.
     * 每一個 backend 用 Map<String, String> 存名字跟 URL。
     */
    private List<Map<String, String>> apis;

    public List<Map<String, String>> getApis() {
        return apis;
    }

    public void setApis(List<Map<String, String>> apis) {
        this.apis = apis;
    }
}
