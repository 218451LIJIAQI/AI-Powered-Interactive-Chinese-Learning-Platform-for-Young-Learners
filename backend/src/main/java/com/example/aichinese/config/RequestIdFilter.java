package com.example.aichinese.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    /**
     * Hard cap on the request-id length. Bounded so a malicious client
     * cannot inflate every log line with a megabyte payload, and so the
     * value comfortably fits inside the {@code 8 KiB} default servlet
     * header budget alongside auth headers and cookies.
     */
    private static final int MAX_REQUEST_ID_LENGTH = 120;

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
        MDC.put("requestId", requestId);
        response.setHeader(RequestIdHolder.HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("requestId");
            RequestIdHolder.clear();
        }
    }

    private static String normalizeRequestId(String requestId) {
        if (requestId == null) {
            return null;
        }

        // strip() (vs trim()) is Unicode-aware so a stray CJK fullwidth space
        // would not survive normalisation differently from ASCII whitespace.
        String stripped = requestId.strip();
        if (stripped.isEmpty()) {
            return null;
        }

        // Sanitise before echoing back in a response header.
        // A client-supplied value containing CR/LF (\r, \n) or other control
        // characters could split the HTTP response and inject arbitrary
        // response headers (HTTP response splitting / header injection).
        // Replace every character outside the printable ASCII range
        // (0x20\u20130x7E) with an underscore so the reflected value is always
        // safe to embed in a response header without further encoding.
        String sanitized = toPrintableAscii(stripped);

        if (sanitized.isBlank()) {
            return null;
        }

        // Keep request ids log-friendly and bounded for response headers.
        if (sanitized.length() > MAX_REQUEST_ID_LENGTH) {
            return sanitized.substring(0, MAX_REQUEST_ID_LENGTH);
        }
        return sanitized;
    }

    /**
     * Replaces every character outside the printable ASCII range (U+0020\u2013U+007E)
     * with an underscore. This ensures the value is safe to embed in an HTTP
     * response header without triggering response-splitting attacks.
     */
    private static String toPrintableAscii(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append((c >= 0x20 && c <= 0x7E) ? c : '_');
        }
        return sb.toString();
    }
}
