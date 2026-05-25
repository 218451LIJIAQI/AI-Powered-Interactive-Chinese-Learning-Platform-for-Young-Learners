package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
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
        assertThat(api.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(api.getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "Accept", "X-Requested-With", "X-Request-ID");
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
    void fallsBackToLocalDevOriginsWhenAllOriginsAreBlankOrWildcard() {
        CorsConfiguration api = apiCorsConfiguration("  ,  *  ,");

        assertThat(api.getAllowedOrigins()).containsExactlyElementsOf(CorsConfig.defaultLocalDevOrigins());
    }

    @Test
    void trimsSurroundingWhitespaceOnOrigins() {
        CorsConfiguration api = apiCorsConfiguration("  http://localhost:5173 , https://example.test  ");

        assertThat(api.getAllowedOrigins())
                .containsExactly("http://localhost:5173", "https://example.test");
    }

    @Test
    void defaultLocalDevOriginsCoverLoopbackAndViteFallbackPorts() {
        assertThat(CorsConfig.defaultLocalDevOrigins())
                .containsExactly(
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

    @Test
    void prodProfileRequiresExplicitCorsOrigins() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        CorsConfig prodConfig = new CorsConfig(environment);

        assertThatThrownBy(() -> prodConfig.corsConfigurationSource("  ,  *  ,"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS");
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
                .map(Map.Entry::getValue)
                .toList();
        assertThat(matched).hasSize(1);
        return matched.get(0);
    }
}
