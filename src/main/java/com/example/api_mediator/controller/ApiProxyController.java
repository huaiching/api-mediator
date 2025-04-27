package com.example.api_mediator.controller;

import com.example.api_mediator.service.ProxyService;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * API 中介控制器，負責處理進來的 HTTP 請求
 */
@Hidden
@RestController
@RequestMapping("/proxy")
public class ApiProxyController {

    private final ProxyService proxyService;

    /**
     * 建構子，注入中介服務
     */
    public ApiProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    /**
     * 取得 Swagger 多後端設定
     *
     * @return Swagger 配置 JSON
     */
    @GetMapping("/swagger-config")
    public ObjectNode swaggerConfig() {
        return proxyService.buildSwaggerConfig();
    }

    /**
     * 代理所有經由 /proxy/{backendName}/ 的請求
     *
     * @param backendName 後端名稱
     * @param request     原始 HTTP 請求
     * @return 後端回應
     * @throws IOException 讀取請求錯誤
     */
    @RequestMapping(value = "/{backendName}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
    public Mono<ResponseEntity<byte[]>> proxyRequest(@PathVariable String backendName, HttpServletRequest request) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return Mono.just(ResponseEntity.ok().headers(proxyService.buildCorsHeaders(request)).body(new byte[0]));
        }
        return proxyService.proxy(backendName, request);
    }
}
