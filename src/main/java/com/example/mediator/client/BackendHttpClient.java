package com.example.mediator.client;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * 後端 HTTP 客戶端，負責轉送 HTTP 請求
 */
@Component
public class BackendHttpClient {

    private final WebClient webClient;

    /**
     * 建構子，注入 WebClient.Builder
     */
    public BackendHttpClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    /**
     * 發送轉發請求到後端系統
     *
     * @param url     目標 URL
     * @param method  HTTP 方法
     * @param headers 請求標頭
     * @param body    請求內容
     * @return 回傳 ResponseEntity，包含後端回應資料
     */
    public Mono<ResponseEntity<byte[]>> forwardRequest(String url, HttpMethod method, HttpHeaders headers, byte[] body) {
        return webClient.method(method)
                .uri(url)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(body.length > 0 ? body : new byte[0])
                .retrieve()
                .toEntity(byte[].class);
    }
}
