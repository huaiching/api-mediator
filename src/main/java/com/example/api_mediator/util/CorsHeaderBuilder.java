package com.example.api_mediator.util;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

@Component
public class CorsHeaderBuilder {

    public HttpHeaders build(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        String origin = Optional.ofNullable(request.getHeader("Origin")).orElse("*");

        headers.add("Access-Control-Allow-Origin", origin);
        headers.add("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        headers.add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        headers.add("Access-Control-Expose-Headers", "*");
        headers.add("Access-Control-Max-Age", "3600");

        return headers;
    }
}