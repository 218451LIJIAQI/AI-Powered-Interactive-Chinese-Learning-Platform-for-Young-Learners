package com.example.aichinese.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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
 * set of Vite dev/preview ports ({@code http://localhost:5173..5183}
 * and the {@code 127.0.0.1} variants). Vite hot-restart sometimes
 * shifts to the next available port if the default is busy; the wider
 * fallback set keeps local development working without manual
 * reconfiguration.</p>
 */
@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);
    private static final String DEFAULT_LOCAL_DEV_ORIGINS =
            "http://localhost:5173,http://127.0.0.1:5173,"
                    + "http://localhost:5174,http://127.0.0.1:5174,"
                    + "http://localhost:5175,http://127.0.0.1:5175,"
                    + "http://localhost:5176,http://127.0.0.1:5176,"
                    + "http://localhost:5177,http://127.0.0.1:5177,"
                    + "http://localhost:5178,http://127.0.0.1:5178,"
                    + "http://localhost:5179,http://127.0.0.1:5179,"
                    + "http://localhost:5180,http://127.0.0.1:5180,"
                    + "http://localhost:5181,http://127.0.0.1:5181,"
                    + "http://localhost:5182,http://127.0.0.1:5182,"
                    + "http://localhost:5183,http://127.0.0.1:5183";
    private final Environment environment;

    public CorsConfig(Environment environment) {
        this.environment = environment;
    }

    CorsConfig() {
        this.environment = null;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:" + DEFAULT_LOCAL_DEV_ORIGINS + "}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();

        // Build an explicit allow-list of exact origins. We deliberately avoid
        // CorsConfiguration#addAllowedOriginPattern + setAllowCredentials(true)
        // because if an operator ever sets APP_CORS_ALLOWED_ORIGINS to '*',
        // the pattern form would echo the caller's origin back with
        // Access-Control-Allow-Credentials: true, i.e. a CSRF/credential-leak
        // vulnerability. The exact-origin form below rejects '*' outright when
        // credentials are enabled, which is the behaviour we want.
        List<String> exactOrigins = new ArrayList<>();
        for (String rawOrigin : allowedOrigins.split(",")) {
            String origin = rawOrigin.trim();
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
            exactOrigins.add(origin);
        }

        if (exactOrigins.isEmpty()) {
            if (isProductionProfileActive()) {
                throw new IllegalStateException("APP_CORS_ALLOWED_ORIGINS must contain explicit origins when the prod profile is active.");
            }
            // Fall back to a small allow-list of loopback Vite origins. This
            // keeps local development and preview QA working when Vite moves
            // from the usual 5173/5174 ports to the next available port instead
            // of silently breaking the browser with blocked credentialed
            // requests.
            exactOrigins.addAll(defaultLocalDevOrigins());
            log.warn("No explicit CORS origins were configured; falling back to local dev origins {}.",
                    defaultLocalDevOrigins());
        }

        configuration.setAllowedOrigins(exactOrigins);
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private boolean isProductionProfileActive() {
        return environment != null && Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .map(String::toLowerCase)
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }

    static List<String> defaultLocalDevOrigins() {
        return List.of(
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174",
                "http://localhost:5175",
                "http://127.0.0.1:5175",
                "http://localhost:5176",
                "http://127.0.0.1:5176",
                "http://localhost:5177",
                "http://127.0.0.1:5177",
                "http://localhost:5178",
                "http://127.0.0.1:5178",
                "http://localhost:5179",
                "http://127.0.0.1:5179",
                "http://localhost:5180",
                "http://127.0.0.1:5180",
                "http://localhost:5181",
                "http://127.0.0.1:5181",
                "http://localhost:5182",
                "http://127.0.0.1:5182",
                "http://localhost:5183",
                "http://127.0.0.1:5183");
    }
}
