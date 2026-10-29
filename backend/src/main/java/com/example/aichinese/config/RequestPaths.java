package com.example.aichinese.config;

import java.util.Objects;

import jakarta.servlet.http.HttpServletRequest;

/** The application-relative request path, shared by the request filters and the teacher approval interceptor. */
final class RequestPaths {

    private RequestPaths() {
    }

    /**
     * The request URI without the servlet context path: empty at the context root, unchanged for a URI outside the
     * context path, and empty for a request without a URI.
     */
    static String applicationPath(HttpServletRequest request) {
        String requestUri = Objects.requireNonNullElse(request.getRequestURI(), "");
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && requestUri.startsWith(contextPath)
                ? requestUri.substring(contextPath.length())
                : requestUri;
    }
}
