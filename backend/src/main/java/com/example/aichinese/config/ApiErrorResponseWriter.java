package com.example.aichinese.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.example.aichinese.dto.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Small response writer for servlet filters that reject a request before it
 * reaches Spring MVC and therefore cannot rely on {@code GlobalExceptionHandler}
 * to render the platform's standard JSON envelope.
 */
final class ApiErrorResponseWriter {

    private ApiErrorResponseWriter() {
    }

    static void write(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(response, "response");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(message, "message");

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(message, List.of(message)));
    }
}
