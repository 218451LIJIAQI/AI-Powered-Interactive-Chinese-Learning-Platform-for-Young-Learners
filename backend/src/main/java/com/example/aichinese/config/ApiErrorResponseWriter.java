package com.example.aichinese.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

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
public final class ApiErrorResponseWriter {

    private ApiErrorResponseWriter() {
    }

    public static void write(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            HttpStatus status,
            String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.failure(message));
    }
}
