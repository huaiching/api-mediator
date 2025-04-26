package com.example.api_mediator.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;

@Component
public class OpenApiModifier {

    private final ObjectMapper objectMapper;

    public OpenApiModifier(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] modifyResponse(byte[] originalResponse, String backendName, HttpServletRequest request) {
        try {
            String originalJson = new String(originalResponse, StandardCharsets.UTF_8);
            String modifiedJson = modifyOpenApiJson(originalJson, backendName, request);
            return modifiedJson.getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return originalResponse; // 出錯時返回原始內容
        }
    }

    private String modifyOpenApiJson(String json, String backendName, HttpServletRequest request) throws Exception {
        var rootNode = objectMapper.readTree(json);

        // 移除原有的 servers 配置
        if (rootNode.has("servers")) {
            ((com.fasterxml.jackson.databind.node.ObjectNode) rootNode).remove("servers");
        }

        // 添加新的 server 配置，指向代理服務
        String gatewayUrl = String.format("%s://%s:%d/proxy/%s",
                request.getScheme(),
                request.getServerName(),
                request.getServerPort(),
                backendName);

        var serversNode = objectMapper.createArrayNode();
        var serverNode = objectMapper.createObjectNode();
        serverNode.put("url", gatewayUrl);
        serversNode.add(serverNode);

        ((com.fasterxml.jackson.databind.node.ObjectNode) rootNode).set("servers", serversNode);

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rootNode);
    }
}