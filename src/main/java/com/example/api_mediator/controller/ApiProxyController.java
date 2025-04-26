package com.example.api_mediator.controller;

import com.example.api_mediator.config.BackendConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * API 代理控制器
 * 用來接收前端請求，轉發到不同的後端服務
 */
@RestController
@RequestMapping("/proxy")
public class ApiProxyController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final BackendConfig backendConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    /**
     * 建構子，初始化後端設定與 WebClient
     */
    public ApiProxyController(BackendConfig backendConfig, WebClient.Builder webClientBuilder) {
        this.backendConfig = backendConfig;
        this.webClient = webClientBuilder.build();
    }

    /**
     * 核心代理方法
     *
     * @param backendName 後端名稱
     * @param request HttpServletRequest 請求物件
     * @return Mono<ResponseEntity<byte[]>> 回應結果
     */
    @RequestMapping(value = "/{backendName}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
    public Mono<ResponseEntity<byte[]>> proxyRequest(@PathVariable String backendName, HttpServletRequest request) throws IOException {
        // 1. 處理 CORS 預檢請求 (OPTIONS)
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            HttpHeaders corsHeaders = buildCorsHeaders(request);
            return Mono.just(ResponseEntity.ok().headers(corsHeaders).body(new byte[0]));
        }

        // 2. 找到對應後端設定
        Map<String, String> backend = backendConfig.getApis().stream()
                .filter(api -> backendName.equals(api.get("name")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Backend not found: " + backendName));

        // 3. 組成完整後端 URL
        String backendUrl = backend.get("url");
        String requestPath = request.getRequestURI().replace("/proxy/" + backendName, "");
        String fullUrl = backendUrl + requestPath;
        log.info("Proxying request to: {}", fullUrl);

        // 4. 建立 WebClient 請求
        WebClient.RequestBodyUriSpec requestSpec = webClient.method(HttpMethod.valueOf(request.getMethod()));

        // 5. 複製前端傳來的標頭，排除 Host、Content-Length
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Content-Length")) {
                headers.add(name, request.getHeader(name));
            }
        });

        // 6. 讀取請求 Body（如果有）
        byte[] requestBody = request.getInputStream().readAllBytes();

        // 7. 轉發請求到後端
        return requestSpec
                .uri(fullUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(requestBody.length > 0 ? requestBody : new byte[0])
                .retrieve()
                .toEntity(byte[].class)
                .flatMap(responseEntity -> {
                    byte[] responseBody = responseEntity.getBody();
                    int statusCode = responseEntity.getStatusCodeValue();

                    // 8. 如果是請求 /api-docs，則修改 OpenAPI 的 servers 資訊
                    if (requestPath.endsWith("/api-docs")) {
                        try {
                            String originalJson = new String(responseBody, StandardCharsets.UTF_8);
                            String modifiedJson = modifyOpenApiJson(originalJson, backendName, request);
                            responseBody = modifiedJson.getBytes(StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            log.error("Failed to modify OpenAPI JSON", e);
                        }
                    }

                    // 9. 組成回應標頭，排除掉某些自動處理的欄位
                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseEntity.getHeaders().forEach((name, values) -> {
                        if (!name.equalsIgnoreCase("Content-Length")
                                && !name.equalsIgnoreCase("Transfer-Encoding")
                                && !name.equalsIgnoreCase("Content-Encoding")
                                && !name.equalsIgnoreCase("Connection")) {
                            values.forEach(value -> responseHeaders.add(name, value));
                        }
                    });
                    responseHeaders.setContentLength(responseBody.length);
                    responseHeaders.addAll(buildCorsHeaders(request)); // 加上 CORS 允許

                    // 10. 正常回傳
                    return Mono.just(ResponseEntity.status(statusCode)
                            .headers(responseHeaders)
                            .body(responseBody));
                })
                .onErrorResume(ex -> {
                    // 11. 發生錯誤時，統一錯誤處理
                    log.error("Proxy error:", ex);

                    int code = 502; // 預設是 Bad Gateway
                    String message = "代理錯誤：" + ex.getMessage();

                    if (ex instanceof java.net.ConnectException) {
                        code = 503;
                        message = "無法連接後端服務";
                    } else if (ex instanceof java.net.SocketTimeoutException) {
                        code = 504;
                        message = "連線逾時";
                    } else if (ex instanceof IllegalArgumentException) {
                        code = 400;
                        message = ex.getMessage();
                    }

                    // 12. 回傳錯誤格式 JSON
                    ObjectNode errorJson = objectMapper.createObjectNode();
                    errorJson.put("code", code);
                    errorJson.put("message", message);

                    byte[] errorBytes;
                    try {
                        errorBytes = objectMapper.writeValueAsBytes(errorJson);
                    } catch (Exception e) {
                        errorBytes = ("{\"code\":502,\"message\":\"Proxy error\"}").getBytes(StandardCharsets.UTF_8);
                    }

                    HttpHeaders headersForError = buildCorsHeaders(request);
                    headersForError.setContentType(MediaType.APPLICATION_JSON);

                    return Mono.just(ResponseEntity.status(code)
                            .headers(headersForError)
                            .body(errorBytes));
                });
    }

    /**
     * 建立 CORS 標頭
     *
     * @param request HttpServletRequest 請求物件
     * @return HttpHeaders 包含 CORS 設定
     */
    private HttpHeaders buildCorsHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String origin = request.getHeader("Origin") != null ? request.getHeader("Origin") : "*";
        headers.add("Access-Control-Allow-Origin", origin);
        headers.add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.add("Access-Control-Expose-Headers", "*");
        headers.add("Access-Control-Max-Age", "3600");
        return headers;
    }

    /**
     * 修改 OpenAPI 規格 JSON，將 servers 欄位指向代理伺服器 URL
     *
     * @param json 後端原始 OpenAPI JSON
     * @param backendName 後端名稱
     * @param request HttpServletRequest 請求物件
     * @return 修改後的 JSON 字串
     * @throws IOException JSON 解析錯誤時拋出
     */
    private String modifyOpenApiJson(String json, String backendName, HttpServletRequest request) throws IOException {
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            if (rootNode.has("servers")) {
                ((ObjectNode) rootNode).remove("servers");
            }

            // 生成新的 servers 指向代理 URL
            String gatewayUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/proxy/" + backendName;
            ArrayNode serversNode = objectMapper.createArrayNode();
            ObjectNode serverNode = objectMapper.createObjectNode();
            serverNode.put("url", gatewayUrl);
            serversNode.add(serverNode);
            ((ObjectNode) rootNode).set("servers", serversNode);

            String modifiedJson = objectMapper.writeValueAsString(rootNode);
            log.info("Modified OpenAPI JSON for {}: {}", backendName, modifiedJson);
            return modifiedJson;
        } catch (Exception e) {
            log.error("Failed to modify OpenAPI JSON for backend: {}", backendName, e);
            return json; // 若失敗，回傳原本的 JSON
        }
    }
}
