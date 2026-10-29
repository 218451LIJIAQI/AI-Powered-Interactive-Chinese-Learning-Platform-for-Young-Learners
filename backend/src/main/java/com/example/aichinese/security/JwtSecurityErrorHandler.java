package com.example.aichinese.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.aichinese.config.ApiErrorResponseWriter;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.util.RequestIdHolder;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** JSON 401 (no or expired token) and 403 (signed in without the required role) answers of the security chain. */
@Component
public class JwtSecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(JwtSecurityErrorHandler.class);

    private final ObjectMapper objectMapper;

    public JwtSecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        if (log.isDebugEnabled()) {
            log.debug("Unauthenticated request rejected with HTTP 401 [requestId={}, method={}, path={}, cause={}]",
                    RequestIdHolder.currentOrDash(),
                    request.getMethod(),
                    request.getRequestURI(),
                    authException == null ? "AuthenticationException" : authException.getClass().getSimpleName());
        }

        // A request that sent a token had a session that ended; one without a token never signed in.
        String message = request.getHeader(HttpHeaders.AUTHORIZATION) == null
                ? FriendlyErrorMessageResolver.signInRequiredMessage()
                : FriendlyErrorMessageResolver.sessionExpiredMessage();
        ApiErrorResponseWriter.write(objectMapper, response, HttpStatus.UNAUTHORIZED, message);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn("Authenticated request rejected with HTTP 403 [requestId={}, method={}, path={}, cause={}]",
                RequestIdHolder.currentOrDash(),
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException == null ? "AccessDeniedException" : accessDeniedException.getClass().getSimpleName());

        ApiErrorResponseWriter.write(objectMapper, response, HttpStatus.FORBIDDEN, FriendlyErrorMessageResolver.permissionDeniedMessage());
    }
}
