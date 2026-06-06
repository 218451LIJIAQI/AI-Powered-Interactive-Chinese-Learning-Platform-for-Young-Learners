package com.example.aichinese.config;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);
    private static final Profiles PROD_PROFILES = Profiles.of("prod", "production");

    private static final String CORS_ORIGIN_PROPERTY = "APP_CORS_ALLOWED_ORIGINS";
    private static final String ORIGIN_LABEL = "CORS allowed origin";
    private static final int VITE_DEV_PORT_START = 5173;
    private static final int VITE_DEV_PORT_END = 5183;
    private static final Duration PREFLIGHT_CACHE_MAX_AGE = Duration.ofHours(1);

    private static final List<String> DEFAULT_LOCAL_DEV_ORIGINS = buildDefaultLocalDevOrigins();
    private static final String DEFAULT_LOCAL_DEV_ORIGINS_CSV = String.join(",", DEFAULT_LOCAL_DEV_ORIGINS);
    private static final List<String> ALLOWED_METHODS = List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization", "Content-Type", "Accept", "X-Requested-With", "X-Request-ID");

    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    CorsConfig() {
        this.environment = null;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:#{T(com.example.aichinese.config.CorsConfig).defaultLocalDevOriginsCsv()}}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();

        boolean productionProfileActive = isProductionProfileActive();
        Set<String> exactOrigins = resolveExactOrigins(
                allowedOrigins,
                productionProfileActive,
                DEFAULT_LOCAL_DEV_ORIGINS);

        configuration.setAllowedOrigins(List.copyOf(exactOrigins));
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(PREFLIGHT_CACHE_MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private boolean isProductionProfileActive() {
        return environment != null && environment.acceptsProfiles(PROD_PROFILES);
    }

    static List<String> defaultLocalDevOrigins() {
        return DEFAULT_LOCAL_DEV_ORIGINS;
    }

    public static String defaultLocalDevOriginsCsv() {
        return DEFAULT_LOCAL_DEV_ORIGINS_CSV;
    }

    static Set<String> resolveExactOrigins(
            String allowedOrigins,
            boolean productionProfileActive,
            Collection<String> fallbackOrigins) {
        Set<String> exactOrigins = new LinkedHashSet<>();
        String configuredOrigins = allowedOrigins == null ? "" : allowedOrigins;
        for (String rawOrigin : configuredOrigins.split(",")) {
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
            exactOrigins.addAll(fallbackOrigins);
            log.warn("No explicit CORS origins were configured; falling back to local dev origins {}.",
                    fallbackOrigins);
        }

        return exactOrigins;
    }

    private static List<String> buildDefaultLocalDevOrigins() {
        List<String> origins = new ArrayList<>();
        origins.add("http://localhost:4173");
        origins.add("http://127.0.0.1:4173");
        for (int port = VITE_DEV_PORT_START; port <= VITE_DEV_PORT_END; port++) {
            origins.add("http://localhost:" + port);
            origins.add("http://127.0.0.1:" + port);
        }
        return List.copyOf(origins);
    }

    private static String normalizeOrigin(String origin, boolean productionProfileActive) {
        URI uri = HttpUrlValidation.requireAbsoluteHttpUrl(origin, ORIGIN_LABEL);
        if (uri.getRawPath() != null && !uri.getRawPath().isBlank() && !"/".equals(uri.getRawPath())) {
            throw new IllegalArgumentException("CORS allowed origin must not include a path.");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("CORS allowed origin must not include a query string or fragment.");
        }
        if (productionProfileActive) {
            HttpUrlValidation.requirePublicHttpsUrl(origin, CORS_ORIGIN_PROPERTY, "when the prod profile is active");
        }

        String scheme = HttpUrlValidation.normalizeScheme(uri.getScheme());
        String host = HttpUrlValidation.normalizeHost(uri.getHost());
        String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
        String normalizedHost = host.contains(":") ? "[" + host + "]" : host;
        return scheme + "://" + normalizedHost + port;
    }
}
