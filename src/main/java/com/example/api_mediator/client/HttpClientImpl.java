package com.example.api_mediator.client;

import com.example.api_mediator.util.OpenApiModifier;
import org.apache.http.Header;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import java.nio.charset.StandardCharsets;

@Component
public class HttpClientImpl implements HttpClient {

    private static final String[] EXCLUDED_REQUEST_HEADERS = {"host", "content-length"};
    private static final String[] EXCLUDED_RESPONSE_HEADERS = {
            "content-length", "transfer-encoding", "content-encoding", "connection"
    };

    @Override
    public ResponseEntity<byte[]> executeProxyRequest(
            String backendUrl,
            String backendName,
            HttpServletRequest request,
            OpenApiModifier openApiModifier) throws IOException {

        String targetUrl = buildTargetUrl(backendUrl, request, backendName);
        HttpRequestBase httpRequest = createHttpRequest(request, targetUrl);
        copyRequestHeaders(request, httpRequest);

        try (CloseableHttpClient httpClient = HttpClients.createDefault();
             CloseableHttpResponse backendResponse = httpClient.execute(httpRequest)) {

            byte[] responseBody = EntityUtils.toByteArray(backendResponse.getEntity());

            // 特殊處理 OpenAPI 文檔
            if (isOpenApiRequest(request)) {
                responseBody = openApiModifier.modifyResponse(responseBody, backendName, request);
            }

            HttpHeaders headers = buildResponseHeaders(backendResponse);

            return ResponseEntity.status(backendResponse.getStatusLine().getStatusCode())
                    .headers(headers)
                    .body(responseBody);
        }
    }

    private String buildTargetUrl(String backendUrl, HttpServletRequest request, String backendName) {
        String requestPath = request.getRequestURI().replace("/proxy/" + backendName, "");
        return backendUrl + requestPath;
    }

    private HttpRequestBase createHttpRequest(HttpServletRequest request, String targetUrl) throws IOException {
        String method = request.getMethod().toUpperCase();

        switch (method) {
            case "GET": return new HttpGet(targetUrl);
            case "DELETE": return new HttpDelete(targetUrl);
            case "POST": return createEntityEnclosingRequest(new HttpPost(targetUrl), request);
            case "PUT": return createEntityEnclosingRequest(new HttpPut(targetUrl), request);
            default: throw new IllegalArgumentException("不支援的 HTTP 方法: " + method);
        }
    }

    private HttpEntityEnclosingRequestBase createEntityEnclosingRequest(
            HttpEntityEnclosingRequestBase request,
            HttpServletRequest servletRequest) throws IOException {
        byte[] body = servletRequest.getInputStream().readAllBytes();
        if (body.length > 0) {
            request.setEntity(new ByteArrayEntity(body));
        }
        return request;
    }

    private void copyRequestHeaders(HttpServletRequest source, HttpRequestBase target) {
        source.getHeaderNames().asIterator().forEachRemaining(name -> {
            if (!isExcludedHeader(name, EXCLUDED_REQUEST_HEADERS)) {
                target.setHeader(name, source.getHeader(name));
            }
        });
    }

    private boolean isExcludedHeader(String headerName, String[] excludedHeaders) {
        for (String excluded : excludedHeaders) {
            if (excluded.equalsIgnoreCase(headerName)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOpenApiRequest(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/api-docs");
    }

    private HttpHeaders buildResponseHeaders(CloseableHttpResponse backendResponse) {
        HttpHeaders headers = new HttpHeaders();
        for (Header header : backendResponse.getAllHeaders()) {
            if (!isExcludedHeader(header.getName(), EXCLUDED_RESPONSE_HEADERS)) {
                headers.add(header.getName(), header.getValue());
            }
        }
        return headers;
    }
}