package com.example.api_mediator.controller;

import com.example.api_mediator.config.BackendConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.Header;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/proxy")
public class ApiProxyController {

    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final BackendConfig backendConfig;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ApiProxyController(BackendConfig backendConfig) {
        this.backendConfig = backendConfig;
    }

    @RequestMapping(value = "/{backendName}/**", method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyRequest(@PathVariable String backendName, HttpServletRequest request) throws IOException {
        // 1. 處理 CORS 預檢請求
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            HttpHeaders corsHeaders = buildCorsHeaders(request);
            return ResponseEntity.ok().headers(corsHeaders).body(new byte[0]);
        }

        // 2. 找到後端配置
        Map<String, String> backend = backendConfig.getApis().stream()
                .filter(api -> backendName.equals(api.get("name")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Backend not found: " + backendName));

        String backendUrl = backend.get("url");
        String requestPath = request.getRequestURI().replace("/proxy/" + backendName, "");
        String fullUrl = backendUrl + requestPath;
        log.info("Proxying request to: {}", fullUrl);

        String method = request.getMethod().toUpperCase();
        HttpRequestBase httpRequest;

        // 3. 建立對應的 HTTP 請求
        switch (method) {
            case "GET":
                httpRequest = new HttpGet(fullUrl);
                break;
            case "DELETE":
                httpRequest = new HttpDelete(fullUrl);
                break;
            case "POST":
                HttpPost post = new HttpPost(fullUrl);
                byte[] postBody = request.getInputStream().readAllBytes();
                if (postBody.length > 0) {
                    post.setEntity(new ByteArrayEntity(postBody));
                }
                httpRequest = post;
                break;
            case "PUT":
                HttpPut put = new HttpPut(fullUrl);
                byte[] putBody = request.getInputStream().readAllBytes();
                if (putBody.length > 0) {
                    put.setEntity(new ByteArrayEntity(putBody));
                }
                httpRequest = put;
                break;
            default:
                throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }

        // 4. 複製請求頭，排除 Host 和 Content-Length
        request.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!name.equalsIgnoreCase("Host") && !name.equalsIgnoreCase("Content-Length")) {
                httpRequest.setHeader(name, request.getHeader(name));
            }
        });

        // 5. 發送請求
        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse backendResponse = httpClient.execute(httpRequest)) {

            byte[] responseBody = EntityUtils.toByteArray(backendResponse.getEntity());
            int statusCode = backendResponse.getStatusLine().getStatusCode();

            // 如果是請求 /api-docs，動態修改 servers
            if (requestPath.endsWith("/api-docs")) {
                String originalJson = new String(responseBody, StandardCharsets.UTF_8);
                String modifiedJson = modifyOpenApiJson(originalJson, backendName, request);
                responseBody = modifiedJson.getBytes(StandardCharsets.UTF_8);
            }

            // 構建回應 Header
            HttpHeaders headers = new HttpHeaders();
            for (Header header : backendResponse.getAllHeaders()) {
                String headerName = header.getName();
                if (!headerName.equalsIgnoreCase("Content-Length")
                        && !headerName.equalsIgnoreCase("Transfer-Encoding")
                        && !headerName.equalsIgnoreCase("Content-Encoding")
                        && !headerName.equalsIgnoreCase("Connection")) {
                    headers.add(headerName, header.getValue());
                }
            }
            headers.setContentLength(responseBody.length);
            headers.addAll(buildCorsHeaders(request)); // 添加 CORS

            return ResponseEntity.status(statusCode).headers(headers).body(responseBody);
        }
    }

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

    private String modifyOpenApiJson(String json, String backendName, HttpServletRequest request) throws IOException {
        try {
            JsonNode rootNode = objectMapper.readTree(json);
            if (rootNode.has("servers")) {
                ((ObjectNode) rootNode).remove("servers");
            }

            // 設定 gateway proxy 的 URL
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
            return json; // 出錯時回傳原始 JSON
        }
    }
}
