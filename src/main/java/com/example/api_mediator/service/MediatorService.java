package com.example.api_mediator.service;

import com.example.api_mediator.client.BackendHttpClient;
import com.example.api_mediator.config.properties.ProxyProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 中介邏輯服務，負責轉發請求與修改 OpenAPI JSON
 */
@Service
public class MediatorService {

    private static final Logger logger = Logger.getLogger(MediatorService.class.getName());

    private final ProxyProperties proxyProperties;
    private final BackendHttpClient backendHttpClient;
    private final ObjectMapper objectMapper;


    /**
     * 建構子，注入後端設定與 HTTP 客戶端
     */
    public MediatorService(ProxyProperties proxyProperties, BackendHttpClient backendHttpClient, ObjectMapper objectMapper) {
        this.proxyProperties = proxyProperties;
        this.backendHttpClient = backendHttpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 建立 Swagger UI 所需的多來源 API 配置
     *
     * @return Swagger config JSON
     */
    public ObjectNode buildSwaggerConfig() {
        ObjectNode config = objectMapper.createObjectNode();
        ArrayNode urls = objectMapper.createArrayNode();

        // SWAGGER 加入 中台服務
        ObjectNode urlObj1 = objectMapper.createObjectNode();
        urlObj1.put("name", "中台服務");
        urlObj1.put("url", "/api-docs");
        urls.add(urlObj1);

        // SWAGGER 加入 後端代理服務: 讀取 proxy.apis 的設定
        proxyProperties.getApis().forEach(api -> {
            ObjectNode urlObj2 = objectMapper.createObjectNode();
            urlObj2.put("name", api.getName());
            urlObj2.put("url", "/mediator/" + api.getPath() + "/api-docs");
            urls.add(urlObj2);
        });


        config.set("urls", urls);
        config.put("url", "");
        config.put("validatorUrl", "");
        return config;
    }

    /**
     * 將進來的 HTTP 請求轉發到後端服務
     *
     * @param backendName 後端名稱
     * @param request     前端傳入的 HTTP 請求
     * @return 回傳後端的 HTTP 回應
     * @throws IOException 讀取 request body 發生錯誤
     */
    public Mono<ResponseEntity<byte[]>> proxy(String backendName, HttpServletRequest request) throws IOException {
        var proxyApi = proxyProperties.getApis().stream()
                .filter(api -> backendName.equals(api.getPath()))
                .findFirst().orElse(null);

        if (proxyApi == null) {
            throw new IllegalArgumentException("找不到後端設定：" + backendName);
        }

        String backendUrl = proxyApi.getUrl();
        String prefix = "/mediator/" + proxyApi.getPath();
        String requestPath = request.getRequestURI().replace(prefix, "");
        String queryString = request.getQueryString();
        String fullUrl = backendUrl + requestPath + (queryString != null ? "?" + queryString : "");

        logger.info("中介轉發到後端: " + fullUrl);

        HttpHeaders headers = buildForwardHeaders(request);
        byte[] requestBody = request.getInputStream().readAllBytes();

        return backendHttpClient.forwardRequest(fullUrl, HttpMethod.valueOf(request.getMethod()), headers, requestBody)
                .flatMap(responseEntity -> {
                    byte[] responseBody = responseEntity.getBody() == null ? new byte[0] : responseEntity.getBody();
                    int statusCode = responseEntity.getStatusCodeValue();

                    // 如果是 API 文件，特殊處理內容
                    if (requestPath.endsWith("/api-docs")) {
                        try {
                            String originalJson = new String(responseBody, StandardCharsets.UTF_8);
                            String modifiedJson = modifyOpenApiJson(originalJson, backendName, request);
                            responseBody = modifiedJson.getBytes(StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            logger.log(Level.SEVERE, "修改 OpenAPI JSON 失敗", e);
                        }
                    }

                    HttpHeaders responseHeaders = filterResponseHeaders(responseEntity.getHeaders());
                    responseHeaders.addAll(buildCorsHeaders(request));

                    return Mono.just(ResponseEntity.status(statusCode)
                            .headers(responseHeaders)
                            .body(responseBody));
                })
                .onErrorResume(ex -> Mono.just(buildErrorResponse(request, ex)));
    }

    /**
     * 從原始請求中建立新的轉發 headers
     *
     * @param request 原始請求
     * @return 轉發用的 HttpHeaders
     */
    private HttpHeaders buildForwardHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase("Connection")) {
                headers.add(name, request.getHeader(name));
            }
        });
        return headers;
    }

    /**
     * 過濾後端回應的 headers，移除不必要項目
     *
     * @param originalHeaders 後端回傳的原始 headers
     * @return 過濾後的 headers
     */
    private HttpHeaders filterResponseHeaders(HttpHeaders originalHeaders) {
        HttpHeaders headers = new HttpHeaders();
        originalHeaders.forEach((name, values) -> {
            if (!name.equalsIgnoreCase("Content-Length") && !name.equalsIgnoreCase("Transfer-Encoding")
                    && !name.equalsIgnoreCase("Content-Encoding") && !name.equalsIgnoreCase("Connection")) {
                headers.addAll(name, values);
            }
        });
        return headers;
    }

    /**
     * 建立 CORS 跨域相關 headers
     *
     * @param request 原始請求
     * @return CORS headers
     */
    public HttpHeaders buildCorsHeaders(HttpServletRequest request) {
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
     * 組裝錯誤回應
     *
     * @param request 原始請求
     * @param ex      發生的例外
     * @return 錯誤的 ResponseEntity
     */
    private ResponseEntity<byte[]> buildErrorResponse(HttpServletRequest request, Throwable ex) {
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

        ObjectNode errorJson = objectMapper.createObjectNode();
        errorJson.put("timestamp", Instant.now().toString());
        errorJson.put("status", code);
        errorJson.put("error", HttpStatus.valueOf(code).getReasonPhrase());
        errorJson.put("message", message);
        errorJson.put("path", request.getRequestURI());

        byte[] errorBytes;
        try {
            errorBytes = objectMapper.writeValueAsBytes(errorJson);
        } catch (Exception e) {
            errorBytes = ("{\"code\":502,\"message\":\"代理錯誤\"}").getBytes(StandardCharsets.UTF_8);
        }

        HttpHeaders headers = buildCorsHeaders(request);
        headers.setContentType(MediaType.APPLICATION_JSON);

        return ResponseEntity.status(code).headers(headers).body(errorBytes);
    }

    /**
     * 修改 OpenAPI JSON 的 servers 欄位，改成中介的網址
     *
     * @param json         原始 OpenAPI JSON
     * @param backendName  後端名稱
     * @param request      原始請求
     * @return 修改後的 JSON 字串
     * @throws IOException 讀取或寫入失敗
     */
    private String modifyOpenApiJson(String json, String backendName, HttpServletRequest request) throws IOException {
        JsonNode rootNode = objectMapper.readTree(json);
        ((ObjectNode) rootNode).remove("servers");

        String gatewayUrl = request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort()
                + "/mediator/" + backendName;
        ArrayNode serversNode = objectMapper.createArrayNode();
        ObjectNode serverNode = objectMapper.createObjectNode();
        serverNode.put("url", gatewayUrl);
        serversNode.add(serverNode);
        ((ObjectNode) rootNode).set("servers", serversNode);

        return objectMapper.writeValueAsString(rootNode);
    }
}
