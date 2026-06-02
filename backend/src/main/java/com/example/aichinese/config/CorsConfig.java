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

/**
 * CORS configuration for {@code /api/**}.
 *
 * <p>Builds an explicit allow-list of exact origin URLs from the
 * {@code app.cors.allowed-origins} property, deliberately rejecting
 * the {@code *} wildcard. This is critical because the platform sends
 * {@code Access-Control-Allow-Credentials: true} (so the browser
 * forwards the JWT cookie / Authorization header on cross-origin
 * fetches), and the browser refuses that combination with a wildcard
 * origin. Worse, {@code addAllowedOriginPattern("*") + setAllowCredentials(true)}
 * would make Spring echo the caller's {@code Origin} back as
 * {@code Access-Control-Allow-Origin: <caller>} for every request,
 * which is a CSRF / credential-leak vulnerability.</p>
 *
 * <h2>Production gate</h2>
 * <p>When the {@code prod} profile is active and the operator has not
 * configured any explicit origins, the bean fails fast with
 * {@link IllegalStateException} rather than silently falling back to
 * loopback origins — there is no legitimate scenario in which a
 * production deploy should have permissive defaults.</p>
 *
 * <h2>Local-dev fallback</h2>
 * <p>Outside production, an empty allow-list falls back to a curated
 * set of Vite dev/preview ports ({@code http://localhost:4173} plus
 * {@code http://localhost:5173..5183} and the {@code 127.0.0.1}
 * variants). Vite preview commonly uses 4173, while Vite hot-restart
 * sometimes shifts from 5173 to the next available port; the wider
 * fallback set keeps local development and FYP demos working without
 * manual reconfiguration.</p>
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);
    private static final Profiles PROD_PROFILES = Profiles.of("prod", "production");

    /**
     * Preflight cache lifetime sent via {@code Access-Control-Max-Age}.
     * One hour balances "no stale CORS rules after a config push" against
     * browser preflight chatter. The CORS spec allows up to 86 400 s but
     * many browsers silently cap it lower; one hour is the safe upper
     * bound for predictable behaviour across Chromium, Firefox, and WebKit.
     */
    private static final Duration PREFLIGHT_CACHE_MAX_AGE = Duration.ofHours(1);

    /**
     * Single source of truth for the local-dev fallback. Sequenced as
     * {@code (localhost, 127.0.0.1) x ports} so callers can mentally
     * pair them. Port 4173 is Vite's preview default; Vite hot-restart
     * sometimes shifts from 5173 to the next available port. The wider
     * fallback set keeps local development and FYP demos working without
     * manual reconfiguration.
     */
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

        // Build an explicit allow-list of exact origins. We deliberately avoid
        // CorsConfiguration#addAllowedOriginPattern + setAllowCredentials(true)
        // because if an operator ever sets APP_CORS_ALLOWED_ORIGINS to '*',
        // the pattern form would echo the caller's origin back with
        // Access-Control-Allow-Credentials: true, i.e. a CSRF/credential-leak
        // vulnerability. The exact-origin form below rejects '*' outright when
        // credentials are enabled, which is the behaviour we want.
        boolean productionProfileActive = isProductionProfileActive();
        Set<String> exactOrigins = resolveExactOrigins(
                allowedOrigins,
                productionProfileActive,
                DEFAULT_LOCAL_DEV_ORIGINS);

        configuration.setAllowedOrigins(new ArrayList<>(exactOrigins));
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setAllowedMethods(ALLOWED_METHODS);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(PREFLIGHT_CACHE_MAX_AGE);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    /**
     * Uses {@link Environment#acceptsProfiles(Profiles)} rather than a
     * manual scan of {@code getActiveProfiles()} so that
     * {@code spring.profiles.default=prod} (the common Kubernetes pattern)
     * still triggers the fail-fast guard.
     */
    private boolean isProductionProfileActive() {
        return environment != null && environment.acceptsProfiles(PROD_PROFILES);
    }

    /**
     * Returns the curated immutable list of local-dev origins. Tests pin
     * this exact ordering so the CSV default that ships to local
     * developers cannot drift away from the helper.
     */
    static List<String> defaultLocalDevOrigins() {
        return DEFAULT_LOCAL_DEV_ORIGINS;
    }

    /**
     * Returns the CSV form consumed by the {@code @Value} default expression.
     * Exposed as a static helper because Spring's SpEL evaluator needs
     * a static accessor (the {@code @Value} expression cannot reference
     * instance state at bean-creation time).
     */
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
                // Refuse to silently combine wildcard origins with credentialed
                // requests. Drop the wildcard entry, log a warning, and keep
                // any other explicit origins so the app still starts.
                log.warn("Ignoring wildcard CORS origin '*' because allowCredentials=true. "
                        + "Configure APP_CORS_ALLOWED_ORIGINS with explicit origins instead.");
                continue;
            }
            exactOrigins.add(normalizeOrigin(origin, productionProfileActive));
        }

        if (exactOrigins.isEmpty()) {
            if (productionProfileActive) {
                throw new IllegalStateException("APP_CORS_ALLOWED_ORIGINS must contain explicit origins when the prod profile is active.");
            }
            // Fall back to a small allow-list of loopback Vite origins. This
            // keeps local development and preview QA working when Vite moves
            // from the usual 5173/5174 ports to the next available port instead
            // of silently breaking the browser with blocked credentialed
            // requests.
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
        for (int port = 5173; port <= 5183; port++) {
            origins.add("http://localhost:" + port);
            origins.add("http://127.0.0.1:" + port);
        }
        return List.copyOf(origins);
    }

    private static String normalizeOrigin(String origin, boolean productionProfileActive) {
        URI uri = HttpUrlValidation.requireAbsoluteHttpUrl(origin, "CORS allowed origin");
        if (uri.getRawPath() != null && !uri.getRawPath().isBlank() && !"/".equals(uri.getRawPath())) {
            throw new IllegalArgumentException("CORS allowed origin must not include a path.");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("CORS allowed origin must not include a query string or fragment.");
        }
        if (productionProfileActive) {
            HttpUrlValidation.requirePublicHttpsUrl(origin, "APP_CORS_ALLOWED_ORIGINS", "when the prod profile is active");
        }

        String scheme = HttpUrlValidation.normalizeScheme(uri.getScheme());
        String host = HttpUrlValidation.normalizeHost(uri.getHost());
        String port = uri.getPort() >= 0 ? ":" + uri.getPort() : "";
        String normalizedHost = host.contains(":") ? "[" + host + "]" : host;
        return scheme + "://" + normalizedHost + port;
    }
}
