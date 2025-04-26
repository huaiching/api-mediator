package com.example.api_mediator.controller;

import com.example.api_mediator.service.ApiProxyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/proxy")
public class ApiProxyController {

    private final ApiProxyService apiProxyService;

    public ApiProxyController(ApiProxyService apiProxyService) {
        this.apiProxyService = apiProxyService;
    }

    @RequestMapping(value = "/{backendName}/**",
            method = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
                    RequestMethod.DELETE, RequestMethod.OPTIONS})
    public ResponseEntity<byte[]> proxyRequest(
            @PathVariable String backendName,
            HttpServletRequest request) {
        return apiProxyService.processProxyRequest(backendName, request);
    }
}