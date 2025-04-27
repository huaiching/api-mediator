package com.example.mediator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "測試用中台服務", description = "測試用中台服務 API 接口")
@RestController
@RequestMapping("/test")
public class TextController {

    @Operation(summary = "測試用api", description = "顯示 輸入文字")
    @GetMapping("/showMsg")
    public ResponseEntity<String> showMsg(@RequestParam String showMsg) {
        return ResponseEntity.ok(showMsg);
    }

}