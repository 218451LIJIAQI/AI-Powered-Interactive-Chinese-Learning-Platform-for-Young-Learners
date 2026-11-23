package com.example.aichinese.config;

import java.io.IOException;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.util.RequestIdHolder;
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
 *
 * <p>Registered inside {@link SecurityConfig} after {@code HeaderWriterFilter} and before {@code CorsFilter},
 * so 403 refusals carry full browser security headers and the API's JSON error body.</p>
 */
public class RefreshCookieOriginFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RefreshCookieOriginFilter.class);

    /** The refresh endpoint, which is also the path of the refresh cookie. */
    public static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final String SEC_FETCH_SITE = "Sec-Fetch-Site";
    private static final String SAME_ORIGIN_FETCH = "same-origin";

    private final ObjectMapper objectMapper;
    private final Set<String> trustedOrigins;

    public RefreshCookieOriginFilter(ObjectMapper objectMapper, Set<String> trustedOrigins) {
        this.objectMapper = objectMapper;
        this.trustedOrigins = Set.copyOf(trustedOrigins);
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (requiresOriginCheck(request) && !hasTrustedBrowserOrigin(request)) {
            log.warn("Rejected refresh request with untrusted browser origin [requestId={}, hasOrigin={}, hasReferer={}, hasFetchSite={}]",
                    RequestIdHolder.currentOrDash(),
                    request.getHeader(HttpHeaders.ORIGIN) != null,
                    request.getHeader(HttpHeaders.REFERER) != null,
                    request.getHeader(SEC_FETCH_SITE) != null);
            ApiErrorResponseWriter.write(
                    objectMapper, response, HttpStatus.FORBIDDEN, FriendlyErrorMessageResolver.permissionDeniedMessage());
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean requiresOriginCheck(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod())
                && REFRESH_PATH.equals(UrlPathHelper.defaultInstance.getPathWithinApplication(request));
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
        String requestOrigin = HttpUrlValidation.originOf(request.getScheme(), request.getServerName(), request.getServerPort());
        return origin.equals(requestOrigin) || trustedOrigins.contains(origin);
    }

    /** Normalised like a configured CORS origin, so the two compare equal; {@code null} when it is not an origin. */
    private static String normalizeOriginHeader(String value) {
        try {
            return CorsConfig.normalizeOrigin(value, false);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizeRefererOrigin(String value) {
        try {
            return HttpUrlValidation.originOf(HttpUrlValidation.requireAbsoluteHttpUrl(value, "Referer"));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
