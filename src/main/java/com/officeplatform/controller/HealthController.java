package com.officeplatform.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.officeplatform.dto.response.ApiResponse;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        return ResponseEntity.ok(
            ApiResponse.<Map<String, String>>builder()
                .success(true)
                .message("OK")
                .data(Map.of("status", "UP"))
                .build()
        );
    }

}
