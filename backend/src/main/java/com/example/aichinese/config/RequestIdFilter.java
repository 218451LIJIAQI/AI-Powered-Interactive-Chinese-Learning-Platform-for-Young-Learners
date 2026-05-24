package com.example.aichinese.config;

import java.io.IOException;
import java.util.UUID;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.aichinese.util.RequestIdHolder;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-request servlet filter that establishes the request-id correlation
 * value used for log threading and outbound-call tagging.
 *
 * <h2>Behaviour</h2>
 * <p>For every incoming request the filter:</p>
 * <ol>
 *   <li>Reads the inbound {@value RequestIdHolder#HEADER_NAME} header
 *       and runs it through {@link #normalizeRequestId(String)} so
 *       only safe, printable ASCII reaches the logs and the response.</li>
 *   <li>If the header is missing / empty / unsafe, generates a fresh
 *       {@link UUID#randomUUID() random UUID}.</li>
 *   <li>Stores the value in {@link RequestIdHolder} for the duration of
 *       the request, and echoes it back as the same response header so
 *       the client can correlate logs with what they saw on the wire.</li>
 *   <li>Calls {@link RequestIdHolder#clear()} in a {@code finally}
 *       block — non-negotiable, because servlet containers reuse worker
 *       threads and an un-cleared id would leak into the next request.</li>
 * </ol>
 *
 * <h2>HTTP response splitting defence</h2>
 * <p>Echoing a client-supplied header back into a response is a
 * classic header-injection vector: a value containing CR/LF could
 * truncate the response and inject extra headers. {@link #toPrintableAscii(String)}
 * collapses every non-printable-ASCII character to {@code _} before
 * echoing, preventing this entirely. The 120-character cap keeps the
 * value log-friendly and rules out megabyte-sized abuse payloads.</p>
 *
 * <h2>Filter chain placement</h2>
 * <p>{@link com.example.aichinese.config.SecurityConfig} chains this
 * filter before {@link org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter}
 * so the request id is already in
 * {@link RequestIdHolder} when authentication runs — every 401 / 403
 * log line therefore carries the correlation id of the request that
 * triggered it.</p>
 */
@Component
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String requestId = normalizeRequestId(request.getHeader(RequestIdHolder.HEADER_NAME));
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }

        RequestIdHolder.set(requestId);
        response.setHeader(RequestIdHolder.HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestIdHolder.clear();
        }
    }

    private String normalizeRequestId(String requestId) {
        if (requestId == null) {
            return null;
        }

        String trimmed = requestId.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        // Sanitise before echoing back in a response header.
        // A client-supplied value containing CR/LF (\r, \n) or other control
        // characters could split the HTTP response and inject arbitrary
        // response headers (HTTP response splitting / header injection).
        // Replace every character outside the printable ASCII range
        // (0x20–0x7E) with an underscore so the reflected value is always
        // safe to embed in a response header without further encoding.
        String sanitized = toPrintableAscii(trimmed);

        // Keep request ids log-friendly and bounded for response headers.
        if (sanitized.length() > 120) {
            return sanitized.substring(0, 120);
        }
        return sanitized;
    }

    /**
     * Replaces every character outside the printable ASCII range (U+0020–U+007E)
     * with an underscore. This ensures the value is safe to embed in an HTTP
     * response header without triggering response-splitting attacks.
     */
    private String toPrintableAscii(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append((c >= 0x20 && c <= 0x7E) ? c : '_');
        }
        return sb.toString();
    }
}
