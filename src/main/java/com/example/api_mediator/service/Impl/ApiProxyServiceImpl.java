package com.example.api_mediator.service.Impl;

import com.example.api_mediator.client.HttpClient;
import com.example.api_mediator.config.BackendConfig;
import com.example.api_mediator.service.ApiProxyService;
import com.example.api_mediator.util.CorsHeaderBuilder;
import com.example.api_mediator.util.OpenApiModifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Service
public class ApiProxyServiceImpl implements ApiProxyService {

    private final BackendConfig backendConfig;
    private final HttpClient httpClient;
    private final CorsHeaderBuilder corsHeaderBuilder;
    private final OpenApiModifier openApiModifier;

    public ApiProxyServiceImpl(BackendConfig backendConfig,
                               HttpClient httpClient,
                               CorsHeaderBuilder corsHeaderBuilder,
                               OpenApiModifier openApiModifier) {
        this.backendConfig = backendConfig;
        this.httpClient = httpClient;
        this.corsHeaderBuilder = corsHeaderBuilder;
        this.openApiModifier = openApiModifier;
    }

    /**
     * 處理代理請求
     * @param backendName 後端服務名稱
     * @param request HTTP 請求對象
     * @return 包含響應數據的 ResponseEntity
     */
    @Override
    public ResponseEntity<byte[]> processProxyRequest(
            String backendName,
            HttpServletRequest request) {

        // 處理 CORS 預檢請求
        if (isPreflightRequest(request)) {
            return ResponseEntity.ok()
                    .headers(corsHeaderBuilder.build(request))
                    .body(new byte[0]);
        }

        // 獲取後端配置
        Map<String, String> backend = findBackendConfig(backendName)
                .orElseThrow(() -> new IllegalArgumentException("找不到後端服務: " + backendName));

        // 處理代理請求
        try {
            return httpClient.executeProxyRequest(
                    backend.get("url"),
                    backendName,
                    request,
                    openApiModifier);
        } catch (IOException e) {
            throw new RuntimeException("代理請求處理失敗", e);
        }
    }

    /**
     * 檢查是否為 CORS 預檢請求
     * @param request HTTP 請求對象
     * @return 如果是預檢請求返回 true，否則返回 false
     */
    @Override
    public boolean isPreflightRequest(HttpServletRequest request) {
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    /**
     * 查找後端配置
     * @param backendName 後端服務名稱
     * @return 包含後端配置的 Optional 對象
     */
    @Override
    public Optional<Map<String, String>> findBackendConfig(String backendName) {
        return backendConfig.getApis().stream()
                .filter(api -> backendName.equals(api.get("name")))
                .findFirst();
    }
}