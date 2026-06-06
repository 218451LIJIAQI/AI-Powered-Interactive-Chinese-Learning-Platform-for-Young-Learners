package com.example.aichinese.controller;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ApiResponse<HealthResponse> getHealth() {
        return ApiResponse.success(
                "Service is running",
                new HealthResponse("UP", "backend", Instant.now()));
    }

    public record HealthResponse(String status, String service, Instant timestamp) {
    }
}
