package com.example.aichinese.security;

import java.io.IOException;
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

    private static final Logger log = LoggerFactory.getLogger(JwtAccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public JwtAccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException, ServletException {
        log.warn("Authenticated request rejected with HTTP 403 [requestId={}, method={}, path={}, cause={}]",
                requestId(),
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException == null ? "AccessDeniedException" : accessDeniedException.getClass().getSimpleName());

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        String message = FriendlyErrorMessageResolver.permissionDeniedMessage();
        ApiResponse<Void> body = ApiResponse.failure(message, List.of(message));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /** Resolves the per-request correlation id, falling back to a literal {@code "-"} when none is bound. */
    private static String requestId() {
        String id = RequestIdHolder.get();
        return id == null ? "-" : id;
    }
}
