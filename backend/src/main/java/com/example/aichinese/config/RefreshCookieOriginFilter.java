package com.example.aichinese.config;

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Browser-origin guard for the cookie-authenticated refresh endpoint.
 *
 * <p>The rest of the JSON API is authenticated with bearer access tokens,
 * so Spring CSRF is intentionally disabled. {@code POST /api/v1/auth/refresh}
 * is different: browsers authenticate it with the HttpOnly refresh cookie.
 * This filter therefore rejects browser refresh attempts whose
 * {@code Origin}/{@code Referer} is neither the API's own origin nor a
 * configured frontend CORS origin. Non-browser clients that send no browser
 * origin metadata remain supported.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RefreshCookieOriginFilter extends OncePerRequestFilter {

    private static final Profiles PROD_PROFILES = Profiles.of("prod", "production");
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    private static final String SAME_ORIGIN_FETCH = "same-origin";

    private final ObjectMapper objectMapper;
    private final Set<String> trustedOrigins;

    public RefreshCookieOriginFilter(
            ObjectMapper objectMapper,
            Environment environment,
            @Value("${app.cors.allowed-origins:#{T(com.example.aichinese.config.CorsConfig).defaultLocalDevOriginsCsv()}}")
            String allowedOrigins) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(environment, "environment");
        this.trustedOrigins = Set.copyOf(CorsConfig.resolveExactOrigins(
                allowedOrigins,
                environment.acceptsProfiles(PROD_PROFILES),
                CorsConfig.defaultLocalDevOrigins()));
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (requiresOriginCheck(request) && !hasTrustedBrowserOrigin(request)) {
            reject(response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresOriginCheck(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && REFRESH_PATH.equals(normalizedRequestPath(request));
    }

    private boolean hasTrustedBrowserOrigin(HttpServletRequest request) {
        String originHeader = request.getHeader(HttpHeaders.ORIGIN);
        if (originHeader != null) {
            String origin = normalizeOriginHeader(originHeader);
            return origin != null && isTrustedOrigin(request, origin);
        }

        String refererHeader = request.getHeader(HttpHeaders.REFERER);
        if (refererHeader != null) {
            String refererOrigin = normalizeRefererOrigin(refererHeader);
            return refererOrigin != null && isTrustedOrigin(request, refererOrigin);
        }

        // Modern browsers send Sec-Fetch-Site on navigation/fetch requests.
        // For this cookie-authenticated endpoint, only same-origin browser
        // requests may proceed without Origin/Referer. Same-site is not enough:
        // a sibling subdomain can be same-site but still untrusted.
        String fetchSite = request.getHeader(SEC_FETCH_SITE);
        return fetchSite == null || SAME_ORIGIN_FETCH.equalsIgnoreCase(fetchSite.strip());
    }

    private boolean isTrustedOrigin(HttpServletRequest request, String origin) {
        return origin.equals(requestOrigin(request)) || trustedOrigins.contains(origin);
    }

    private static String normalizedRequestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String requestUri = request.getRequestURI();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri == null ? "" : requestUri;
    }

    private static String requestOrigin(HttpServletRequest request) {
        String scheme = request.getScheme() == null
                ? ""
                : request.getScheme().toLowerCase(Locale.ROOT);
        String host = request.getServerName() == null
                ? ""
                : request.getServerName().toLowerCase(Locale.ROOT);
        int port = request.getServerPort();
        boolean defaultPort = ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        String hostPart = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
        return scheme + "://" + hostPart + (defaultPort || port < 0 ? "" : ":" + port);
    }

    private static String normalizeOriginHeader(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = HttpUrlValidation.requireAbsoluteHttpUrl(value, "Origin");
            if ((uri.getRawPath() != null && !uri.getRawPath().isBlank() && !"/".equals(uri.getRawPath()))
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                return null;
            }
            return originOf(uri);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizeRefererOrigin(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return originOf(HttpUrlValidation.requireAbsoluteHttpUrl(value, "Referer"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String originOf(URI uri) {
        String scheme = HttpUrlValidation.normalizeScheme(uri.getScheme());
        String host = HttpUrlValidation.normalizeHost(uri.getHost());
        String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
        String hostPart = host.contains(":") ? "[" + host + "]" : host;
        return scheme + "://" + hostPart + port;
    }

    private void reject(HttpServletResponse response) throws IOException {
        String message = FriendlyErrorMessageResolver.permissionDeniedMessage();
        ApiErrorResponseWriter.write(objectMapper, response, HttpStatus.FORBIDDEN, message);
    }
}
