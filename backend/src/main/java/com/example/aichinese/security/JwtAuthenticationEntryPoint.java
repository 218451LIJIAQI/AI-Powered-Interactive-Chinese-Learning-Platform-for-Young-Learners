package com.example.aichinese.security;

import java.io.IOException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.ApiErrorResponseWriter;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.util.RequestIdHolder;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Unauthenticated request rejected with HTTP 401 [requestId={}, method={}, path={}, cause={}]",
                    requestId(),
                    request.getMethod(),
                    request.getRequestURI(),
                    authException == null ? "AuthenticationException" : authException.getClass().getSimpleName());
        }

        ApiErrorResponseWriter.write(objectMapper, response, HttpStatus.UNAUTHORIZED, FriendlyErrorMessageResolver.sessionExpiredMessage());
    }

    private static String requestId() {
        String id = RequestIdHolder.get();
        return id == null ? "-" : id;
    }
}
