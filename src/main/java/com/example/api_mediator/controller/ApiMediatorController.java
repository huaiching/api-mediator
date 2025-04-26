package com.example.api_mediator.controller;

import com.example.api_mediator.config.BackendConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * API 中介控制器 (Mediator)
 * 負責轉發前端請求至各後端系統，並處理 swagger 資訊聚合
 */
@RestController
@RequestMapping("/mediator")
public class ApiMediatorController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final BackendConfig backendConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebClient webClient;

    public ApiMediatorController(BackendConfig backendConfig, WebClient.Builder webClientBuilder) {
        this.backendConfig = backendConfig;
        this.webClient = webClientBuilder.build();
    }

    /**
     * 提供 Swagger-UI 動態配置的接口
     * 返回後端 API 列表供 Swagger-UI 顯示切換選單
     *
     * @param request HTTP請求
     * @return Swagger UI 所需的配置
     */
    @GetMapping("/swagger-config")
    public ObjectNode swaggerConfig(HttpServletRequest request) {
        ObjectNode config = objectMapper.createObjectNode();
        ArrayNode urls = objectMapper.createArrayNode();

        backendConfig.getApis().forEach(api -> {
            ObjectNode urlObj = objectMapper.createObjectNode();
            urlObj.put("name", api.get("name"));
            urlObj.put("url", "/mediator/" + api.get("name") + "/api-docs");
            urls.add(urlObj);
        });

        config.set("urls", urls);
        config.put("url", ""); // 不使用單一模式
        config.put("validatorUrl", ""); // 關閉驗證器
        return config;
    }

    /**
     * 中介轉發請求到指定後端
     * 支援 GET / POST / PUT / DELETE / OPTIONS 等方法
     *
     * @param backendName 後端服務名稱
     * @param request 原始HTTP請求
     * @return 後端返回的響應
     * @throws IOException 讀取請求體錯誤時拋出
     */
    @RequestMapping(value = "/{backendName}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
    public Mono<ResponseEntity<byte[]>> proxyRequest(@PathVariable String backendName, HttpServletRequest request) throws IOException {
        // CORS 預檢請求直接回應
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            HttpHeaders corsHeaders = buildCorsHeaders(request);
            return Mono.just(ResponseEntity.ok().headers(corsHeaders).body(new byte[0]));
        }

        // 從設定檔找出對應的後端
        Map<String, String> backend = backendConfig.getApis().stream()
                .filter(api -> backendName.equals(api.get("name")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Backend not found: " + backendName));

        String backendUrl = backend.get("url");

        // 解析原始請求路徑
        String prefix = "/mediator/" + backendName;
        String requestPath = request.getRequestURI().replace(prefix, "");
        String queryString = request.getQueryString();
        String fullUrl = backendUrl + requestPath + (queryString != null ? "?" + queryString : "");

        log.info("Mediator forwarding to: {}", fullUrl);

        WebClient.RequestBodyUriSpec requestSpec = webClient.method(HttpMethod.valueOf(request.getMethod()));

        // 建立轉發用的Header
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Content-Length")) {
                headers.add(name, request.getHeader(name));
            }
        });

        byte[] requestBody = request.getInputStream().readAllBytes();

        // 執行轉發
        return requestSpec
                .uri(fullUrl)
                .headers(httpHeaders -> httpHeaders.addAll(headers))
                .bodyValue(requestBody.length > 0 ? requestBody : new byte[0])
                .retrieve()
                .toEntity(byte[].class)
                .flatMap(responseEntity -> {
                    byte[] responseBody = responseEntity.getBody();
                    if (responseBody == null) {
                        responseBody = new byte[0];
                    }
                    int statusCode = responseEntity.getStatusCodeValue();

                    // 如果是取得 OpenAPI 文件，則修改其中的 servers 欄位
                    if (requestPath.endsWith("/api-docs")) {
                        try {
                            String originalJson = new String(responseBody, StandardCharsets.UTF_8);
                            String modifiedJson = modifyOpenApiJson(originalJson, backendName, request);
                            responseBody = modifiedJson.getBytes(StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            log.error("Failed to modify OpenAPI JSON", e);
                        }
                    }

                    // 回傳後端響應
                    HttpHeaders responseHeaders = new HttpHeaders();
                    responseEntity.getHeaders().forEach((name, values) -> {
                        if (!name.equalsIgnoreCase("Content-Length")
                                && !name.equalsIgnoreCase("Transfer-Encoding")
                                && !name.equalsIgnoreCase("Content-Encoding")
                                && !name.equalsIgnoreCase("Connection")) {
                            values.forEach(value -> responseHeaders.add(name, value));
                        }
                    });
                    responseHeaders.addAll(buildCorsHeaders(request));

                    return Mono.just(ResponseEntity.status(statusCode)
                            .headers(responseHeaders)
                            .body(responseBody));
                })
                .onErrorResume(ex -> {
                    log.error("Proxy error:", ex);

                    int code = 502;
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

                    // 回傳錯誤格式
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
     * 建立標準 CORS 回應 Header
     *
     * @param request 原始HTTP請求
     * @return HttpHeaders 包含CORS設定
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
     * 修改 OpenAPI JSON，重新設定 servers 欄位
     *
     * @param json 原始 OpenAPI JSON
     * @param backendName 後端服務名稱
     * @param request 原始HTTP請求
     * @return 修改後的 JSON 字串
     * @throws IOException JSON處理錯誤時拋出
     */
    private String modifyOpenApiJson(String json, String backendName, HttpServletRequest request) throws IOException {
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            if (rootNode.has("servers")) {
                ((ObjectNode) rootNode).remove("servers");
            }

            // 動態計算 servers URL
            String gatewayUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + "/mediator/" + backendName;
            ArrayNode serversNode = objectMapper.createArrayNode();
            ObjectNode serverNode = objectMapper.createObjectNode();
            serverNode.put("url", gatewayUrl);
            serversNode.add(serverNode);
            ((ObjectNode) rootNode).set("servers", serversNode);

            return objectMapper.writeValueAsString(rootNode);
        } catch (Exception e) {
            log.error("Failed to modify OpenAPI JSON for backend: {}", backendName, e);
            return json;
        }
    }
}
