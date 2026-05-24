package com.example.aichinese.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Renders the canonical {@code 401 Unauthorized} JSON envelope when a
 * caller is missing credentials, presents an invalid / expired token,
 * or otherwise fails authentication.
 *
 * <p>Returns the {@link FriendlyErrorMessageResolver#sessionExpiredMessage()}
 * sentinel because almost every 401 from this platform is either
 * "session timed out" or "never signed in"; the front-end uses this
 * uniform message to drive its "redirect to login" behaviour. The
 * deliberate vagueness also prevents user-enumeration attacks (we
 * never disclose whether the email exists or whether the token was
 * malformed vs expired vs signed with a rotated secret).</p>
 *
 * <p>The {@code Cache-Control: no-store} / {@code Pragma: no-cache}
 * pair is required so an intermediate proxy or browser cannot cache
 * the 401 response and replay it for a later legitimate request after
 * the user signs in.</p>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        String message = FriendlyErrorMessageResolver.sessionExpiredMessage();
        ApiResponse<Void> body = ApiResponse.failure(message, List.of(message));
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
