package com.example.aichinese.config;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import lombok.RequiredArgsConstructor;

@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    private static final String CORS_ORIGIN_PROPERTY = "APP_CORS_ALLOWED_ORIGINS";
    private static final String ORIGIN_LABEL = "CORS allowed origin";
    private static final Duration PREFLIGHT_CACHE_MAX_AGE = Duration.ofHours(1);

    /** The Vite dev server (strict port 5174); used outside production when no origin is configured. */
    static final List<String> DEFAULT_LOCAL_DEV_ORIGINS = List.of(
            "http://localhost:5174",
            "http://127.0.0.1:5174");
    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of("Authorization", "Content-Type", "Accept");
    /**
     * Response headers the web app reads (the sign-in pause from Retry-After, a download's file name). A browser
     * hides every other header than the CORS-safelisted ones from a page on another origin unless they are listed.
     */
    private static final List<String> EXPOSED_HEADERS = List.of(HttpHeaders.RETRY_AFTER, HttpHeaders.CONTENT_DISPOSITION);

    /** Immutable, validated browser origins shared by CORS configuration and {@link RefreshCookieOriginFilter}. */
    record CorsAllowedOrigins(List<String> ordered, Set<String> exact) {
        static CorsAllowedOrigins of(Set<String> resolved) {
            return new CorsAllowedOrigins(List.copyOf(resolved), Set.copyOf(resolved));
        }
    }

    private final Environment environment;

    @Bean
    CorsAllowedOrigins corsAllowedOrigins(@Value("${app.cors.allowed-origins:}") String allowedOrigins) {
        return CorsAllowedOrigins.of(resolveExactOrigins(allowedOrigins, ProductionProfiles.isActive(environment)));
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsAllowedOrigins allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins.ordered());
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setExposedHeaders(EXPOSED_HEADERS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(PREFLIGHT_CACHE_MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static Set<String> resolveExactOrigins(String allowedOrigins, boolean productionProfileActive) {
        Set<String> exactOrigins = new LinkedHashSet<>();
        for (String rawOrigin : allowedOrigins.split(",")) {
            String origin = rawOrigin.strip();
            if (origin.isEmpty()) {
                continue;
            }
            if ("*".equals(origin)) {
                log.warn("Ignoring wildcard CORS origin '*' because allowCredentials=true. "
                        + "Configure APP_CORS_ALLOWED_ORIGINS with explicit origins instead.");
                continue;
            }
            exactOrigins.add(normalizeOrigin(origin, productionProfileActive));
        }

        if (exactOrigins.isEmpty()) {
            if (productionProfileActive) {
                throw new IllegalStateException(
                        CORS_ORIGIN_PROPERTY + " must contain explicit origins when the prod profile is active.");
            }
            exactOrigins.addAll(DEFAULT_LOCAL_DEV_ORIGINS);
        }

        return exactOrigins;
    }

    static String normalizeOrigin(String origin, boolean productionProfileActive) {
        URI uri = HttpUrlValidation.requireAbsoluteHttpUrl(origin, ORIGIN_LABEL);
        if (uri.getRawPath() != null && !uri.getRawPath().isBlank() && !"/".equals(uri.getRawPath())) {
            throw new IllegalArgumentException("CORS allowed origin must not include a path.");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("CORS allowed origin must not include a query string or fragment.");
        }
        if (productionProfileActive) {
            HttpUrlValidation.requirePublicHttpsUrl(uri, CORS_ORIGIN_PROPERTY, "when the prod profile is active");
        }
        return HttpUrlValidation.originOf(uri);
    }
}
