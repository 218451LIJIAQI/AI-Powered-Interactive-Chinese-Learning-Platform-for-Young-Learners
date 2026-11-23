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

    /** Stripped again after the cut, so the header, the log context and the response body carry the same id. */
    private static String normalizeRequestId(String requestId) {
        String stripped = TextUtils.trimToNull(requestId);
        if (stripped == null) {
            return null;
        }
        String bounded = TextUtils.trimToNull(stripped.substring(0, Math.min(stripped.length(), MAX_REQUEST_ID_LENGTH)));
        if (bounded == null) {
            return null;
        }
        StringBuilder sanitized = new StringBuilder(bounded.length());
        boolean hasSafeChar = false;
        for (int i = 0; i < bounded.length(); i++) {
            char c = bounded.charAt(i);
            if (isAllowedRequestIdChar(c)) {
                sanitized.append(c);
                hasSafeChar = true;
            } else {
                sanitized.append('_');
            }
        }
        return hasSafeChar ? sanitized.toString() : null;
    }

    private static boolean isAllowedRequestIdChar(char c) {
        return (c >= 'a' && c <= 'z')
                || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9')
                || c == '.'
                || c == '_'
                || c == ':'
                || c == '-';
    }
}
