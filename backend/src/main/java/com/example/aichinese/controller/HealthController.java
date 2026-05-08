package com.example.aichinese.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> getHealth() {
        return ApiResponse.success(
                "Service is running",
                Map.of(
                        "status", "UP",
                        "service", "backend",
                        "timestamp", Instant.now().toString()));
    }
}
