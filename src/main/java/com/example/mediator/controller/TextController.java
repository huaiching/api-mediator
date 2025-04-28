package com.example.mediator.controller;

import com.example.mediator.dto.ClntDto;
import com.example.mediator.utils.ApiWebClientUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Tag(name = "測試用中台服務", description = "測試用中台服務 API 接口")
@RestController
@RequestMapping("/test")
public class TextController {
    @Autowired
    private ApiWebClientUtils apiWebClientUtils;

    @Operation(summary = "測試用 api", description = "顯示 輸入文字")
    @GetMapping("/showMsg")
    public ResponseEntity<String> showMsg(@RequestParam String showMsg) {
        return ResponseEntity.ok(showMsg);
    }

    @Operation(summary = "測試用 api: select clnt", description = "select clnt")
    @GetMapping("/findById")
    public ResponseEntity<ClntDto> findById(@RequestParam String clientId) {

        // 設定 網域
        String BASE_URL = "http://localhost:9091";
        String apiName = "/clnt/findById";
        // 設定參數
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("clientId", clientId);


        ClntDto clntDto = apiWebClientUtils.callPostApiAndGetDto(BASE_URL, apiName, parameters, ClntDto.class);

        return ResponseEntity.ok(clntDto);
    }

}