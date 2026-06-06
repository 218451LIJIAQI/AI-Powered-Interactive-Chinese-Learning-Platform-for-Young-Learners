package com.example.aichinese.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.util.RequestIdHolder;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(JwtAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn("Authenticated request rejected with HTTP 403 [requestId={}, method={}, path={}, cause={}]",
                requestId(),
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException == null ? "AccessDeniedException" : accessDeniedException.getClass().getSimpleName());

        writeFailure(response, HttpStatus.FORBIDDEN, FriendlyErrorMessageResolver.permissionDeniedMessage());
    }

    private void writeFailure(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        ApiResponse<Void> body = ApiResponse.failure(message, List.of(message));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private static String requestId() {
        String id = RequestIdHolder.get();
        return id == null ? "-" : id;
    }
}
