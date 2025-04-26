package com.example.api_mediator.service;

import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

public interface ApiProxyService {
    /**
     * 處理代理請求
     * @param backendName 後端服務名稱
     * @param request HTTP 請求對象
     * @return 包含響應數據的 ResponseEntity
     */
    ResponseEntity<byte[]> processProxyRequest(String backendName, HttpServletRequest request);

    /**
     * 檢查是否為 CORS 預檢請求
     * @param request HTTP 請求對象
     * @return 如果是預檢請求返回 true，否則返回 false
     */
    boolean isPreflightRequest(HttpServletRequest request);

    /**
     * 查找後端配置
     * @param backendName 後端服務名稱
     * @return 包含後端配置的 Optional 對象
     */
    Optional<Map<String, String>> findBackendConfig(String backendName);
}