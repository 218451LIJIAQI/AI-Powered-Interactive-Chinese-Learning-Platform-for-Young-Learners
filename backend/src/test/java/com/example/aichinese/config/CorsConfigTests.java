package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Unit tests for {@link CorsConfig}. The bean is small but security-sensitive,
 * so the contract is locked down with explicit assertions:
 *
 * <ul>
 *   <li>The wildcard origin '*' must never combine with credentials=true.</li>
 *   <li>Empty / blank entries in the env value must not break the bean.</li>
 *   <li>Multiple comma-separated origins must all be honoured.</li>
 * </ul>
 */
class CorsConfigTests {

    private final CorsConfig config = new CorsConfig();

    @Test
    void registersExactOriginsForApiPath() {
        CorsConfiguration api = apiCorsConfiguration("http://localhost:5173,https://example.test");

        assertThat(api).isNotNull();
        assertThat(api.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://example.test");
        assertThat(api.getAllowCredentials()).isTrue();
        // Pattern-based allow list must remain unused so '*' cannot ever sneak
        // through with credentials enabled.
        assertThat(api.getAllowedOriginPatterns()).isNull();
    }

    @Test
    void dropsWildcardOriginWhileKeepingExplicitOnes() {
        CorsConfiguration api = apiCorsConfiguration("*,http://localhost:5173");

        assertThat(api.getAllowedOrigins()).containsExactly("http://localhost:5173");
    }

    @Test
    void fallsBackToLocalhostWhenAllOriginsAreBlankOrWildcard() {
        CorsConfiguration api = apiCorsConfiguration("  ,  *  ,");

        // We deliberately fall back to the dev origin instead of leaving the
        // app with zero allowed origins — that would silently break every
        // browser request and look like a separate bug at runtime.
        assertThat(api.getAllowedOrigins()).containsExactly("http://localhost:5173");
    }

    @Test
    void trimsSurroundingWhitespaceOnOrigins() {
        CorsConfiguration api = apiCorsConfiguration("  http://localhost:5173 , https://example.test  ");

        assertThat(api.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://example.test");
    }

    private CorsConfiguration apiCorsConfiguration(String allowedOrigins) {
        CorsConfigurationSource source = config.corsConfigurationSource(allowedOrigins);
        assertThat(source).isInstanceOf(UrlBasedCorsConfigurationSource.class);
        UrlBasedCorsConfigurationSource urlSource = (UrlBasedCorsConfigurationSource) source;
        // The bean registers /api/** specifically; ensure it is the same
        // configuration we just built rather than a fallback default.
        List<CorsConfiguration> matched = urlSource.getCorsConfigurations()
                .entrySet()
                .stream()
                .filter(entry -> entry.getKey().equals("/api/**"))
                .map(java.util.Map.Entry::getValue)
                .toList();
        assertThat(matched).hasSize(1);
        return matched.get(0);
    }
}
