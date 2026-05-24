package com.example.aichinese.controller;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;

/**
 * Lightweight liveness endpoint for orchestrators and uptime
 * monitors. Returns a fixed-shape {@code UP} payload plus an ISO-8601
 * server timestamp.
 *
 * <p>Intentionally unauthenticated, owns no business logic, and does
 * not touch the database — so a degraded persistence layer cannot
 * make the health endpoint report {@code DOWN} and trip an unrelated
 * failover. Deeper readiness checks (DB, AI, email, payment) belong
 * in dedicated probes that run out-of-band.</p>
 */
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
