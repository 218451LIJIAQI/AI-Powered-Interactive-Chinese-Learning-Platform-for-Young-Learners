package com.example.aichinese.security;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.util.RequestIdHolder;
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

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException, ServletException {
        // 401 entries are normal background noise (token expiry, anonymous browses to a
        // protected endpoint) and so are logged at DEBUG rather than WARN to avoid
        // drowning the security ops channel \u2014 access-denied (403) is the elevated event.
        if (log.isDebugEnabled()) {
            log.debug("Unauthenticated request rejected with HTTP 401 [requestId={}, method={}, path={}, cause={}]",
                    requestId(),
                    request.getMethod(),
                    request.getRequestURI(),
                    authException == null ? "AuthenticationException" : authException.getClass().getSimpleName());
        }

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        String message = FriendlyErrorMessageResolver.sessionExpiredMessage();
        ApiResponse<Void> body = ApiResponse.failure(message, List.of(message));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    /** Resolves the per-request correlation id, falling back to a literal {@code "-"} when none is bound. */
    private static String requestId() {
        String id = RequestIdHolder.get();
        return id == null ? "-" : id;
    }
}
