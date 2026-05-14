package com.example.aichinese.config;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class CorsConfig {

    private static final Logger log = LoggerFactory.getLogger(CorsConfig.class);

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();

        // Build an explicit allow-list of exact origins. We deliberately avoid
        // CorsConfiguration#addAllowedOriginPattern + setAllowCredentials(true)
        // because if an operator ever sets APP_CORS_ALLOWED_ORIGINS to '*',
        // the pattern form would echo the caller's origin back with
        // Access-Control-Allow-Credentials: true — i.e. a CSRF/credential-leak
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
            // Fall back to localhost dev origin so the API never starts in a
            // configuration that would silently block every browser request.
            exactOrigins.add("http://localhost:5173");
            log.warn("No explicit CORS origins were configured; falling back to http://localhost:5173.");
        }

        configuration.setAllowedOrigins(exactOrigins);
        configuration.addAllowedHeader("*");
        configuration.addAllowedMethod("*");
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
