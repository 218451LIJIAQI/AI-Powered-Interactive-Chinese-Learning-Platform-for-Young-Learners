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

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

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

        String stripped = requestId.strip();
        if (stripped.isEmpty()) {
            return null;
        }

        String sanitized = toPrintableAscii(stripped);

        if (sanitized.isBlank()) {
            return null;
        }

        if (sanitized.length() > MAX_REQUEST_ID_LENGTH) {
            return sanitized.substring(0, MAX_REQUEST_ID_LENGTH);
        }
        return sanitized;
    }

    private static String toPrintableAscii(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            sb.append((c >= 0x20 && c <= 0x7E) ? c : '_');
        }
        return sb.toString();
    }
}
