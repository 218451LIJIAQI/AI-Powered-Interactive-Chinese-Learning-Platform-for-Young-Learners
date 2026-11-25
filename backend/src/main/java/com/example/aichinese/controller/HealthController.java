package com.example.aichinese.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.service.DatabaseHealthProbe;
import com.example.aichinese.service.ai.AiAvailabilityService;

import lombok.RequiredArgsConstructor;

/**
 * Public health check: the server answers, the database accepts a connection, and whether the AI provider is
 * configured. The admin sidebar's System status card shows it.
 */
@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
public class HealthController {

    private final DatabaseHealthProbe databaseHealthProbe;
    private final AiAvailabilityService aiAvailabilityService;

    @GetMapping
    public ResponseEntity<ApiResponse<HealthResponse>> getHealth() {
        boolean dbUp = databaseHealthProbe.isDatabaseUp();
        HealthResponse health = new HealthResponse(
                dbUp ? "UP" : "DOWN",
                aiAvailabilityService.getAvailability().available() ? "AVAILABLE" : "UNAVAILABLE");
        ApiResponse<HealthResponse> body = dbUp
                ? ApiResponse.success("Service is running", health)
                : ApiResponse.failure("Service is degraded", health);
        return ResponseEntity.status(dbUp ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .cacheControl(CacheControl.noStore())
                .body(body);
    }

    /** {@code status} is UP when the server and the database work; an unconfigured AI provider is not an outage. */
    public record HealthResponse(String status, String ai) {
    }
}
