package com.example.api_mediator.client;

import com.example.api_mediator.util.OpenApiModifier;
import org.apache.http.client.methods.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public interface HttpClient {
    ResponseEntity<byte[]> executeProxyRequest(
            String backendUrl,
            String backendName,
            HttpServletRequest request,
            OpenApiModifier openApiModifier) throws IOException;
}