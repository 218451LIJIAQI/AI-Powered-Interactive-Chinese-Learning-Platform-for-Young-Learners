package com.example.aichinese.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders the canonical {@code 403 Forbidden} JSON envelope when a
 * caller is authenticated but lacks the role / permission required by
 * the target endpoint.
 *
 * <p>This sits in the Spring Security filter chain — it fires before
 * the {@link com.example.aichinese.exception.GlobalExceptionHandler}
 * gets a chance to translate exceptions, because access-denied
 * decisions occur inside the filter chain and never reach an
 * {@code @ExceptionHandler}. The body shape is therefore intentionally
 * identical to what {@code GlobalExceptionHandler} produces for
 * {@code AccessDeniedException}, so the front-end sees a uniform
 * envelope regardless of which layer rejected the request.</p>
 *
 * <p>The {@code Cache-Control: no-store} / {@code Pragma: no-cache}
 * pair is required so an intermediate proxy / browser cannot cache
 * the 403 response and replay it as the answer to a later legitimate
 * request after the user's role changes.</p>
 */
@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        String message = FriendlyErrorMessageResolver.permissionDeniedMessage();
        ApiResponse<Void> body = ApiResponse.failure(message, List.of(message));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
