package com.example.aichinese.controller;

import java.sql.Connection;
import java.time.Instant;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.service.ai.AiAvailabilityService;

/**
 * Public health check: the server answers, the database accepts a connection, and whether the AI provider is
 * configured. The admin sidebar and dashboard show it as "All systems running".
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private static final int DATABASE_CHECK_TIMEOUT_SECONDS = 2;

    private final DataSource dataSource;
    private final AiAvailabilityService aiAvailabilityService;

    public HealthController(DataSource dataSource, AiAvailabilityService aiAvailabilityService) {
        this.dataSource = dataSource;
        this.aiAvailabilityService = aiAvailabilityService;
    }

    @GetMapping
    public ApiResponse<HealthResponse> getHealth() {
        boolean databaseUp = databaseUp();
        return ApiResponse.success(
                "Service is running",
                new HealthResponse(
                        databaseUp ? "UP" : "DOWN",
                        Instant.now(),
                        databaseUp ? "UP" : "DOWN",
                        aiAvailabilityService.getAvailability().available() ? "AVAILABLE" : "UNAVAILABLE"));
    }

    private boolean databaseUp() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(DATABASE_CHECK_TIMEOUT_SECONDS);
        } catch (Exception unavailable) {
            return false;
        }
    }

    /** {@code status} is UP when the server and the database work; an unconfigured AI provider is not an outage. */
    public record HealthResponse(String status, Instant timestamp, String database, String ai) {
    }
}
