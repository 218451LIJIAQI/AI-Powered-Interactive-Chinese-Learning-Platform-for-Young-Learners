package com.example.aichinese.config;

import java.io.IOException;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.aichinese.util.RequestIdHolder;
import com.example.aichinese.util.TextUtils;

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

        RequestIdHolder.bind(requestId);
        response.setHeader(RequestIdHolder.HEADER_NAME, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestIdHolder.unbind();
        }
    }

    private static String normalizeRequestId(String requestId) {
        String stripped = TextUtils.trimToNull(requestId);
        if (stripped == null) {
            return null;
        }
        String sanitized = toPrintableAscii(stripped);
        return sanitized.length() > MAX_REQUEST_ID_LENGTH ? sanitized.substring(0, MAX_REQUEST_ID_LENGTH) : sanitized;
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
