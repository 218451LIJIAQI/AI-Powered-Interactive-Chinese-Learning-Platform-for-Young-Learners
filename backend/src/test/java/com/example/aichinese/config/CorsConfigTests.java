package com.example.aichinese.config;

import static com.example.aichinese.support.ConfigFixtures.environment;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.cors.CorsConfiguration;
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

    private static final String LOCALHOST_VITE_ORIGIN = "http://localhost:5174";
    private static final String EXAMPLE_ORIGIN = "https://example.test";

    private final CorsConfig config = new CorsConfig(environment());

    @Test
    void registersExactOriginsForApiPath() {
        CorsConfiguration api = apiCorsConfiguration(LOCALHOST_VITE_ORIGIN + "," + EXAMPLE_ORIGIN);

        assertThat(api).isNotNull();
        assertThat(api.getAllowedOrigins())
                .containsExactly(LOCALHOST_VITE_ORIGIN, EXAMPLE_ORIGIN);
        assertThat(api.getAllowCredentials()).isTrue();
        assertThat(api.getAllowedMethods()).containsExactly("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");
        assertThat(api.getAllowedHeaders())
                .containsExactly("Authorization", "Content-Type", "Accept");
        // The web app reads the sign-in pause and a download's file name, which a cross-origin page cannot see otherwise.
        assertThat(api.getExposedHeaders()).containsExactly("Retry-After", "Content-Disposition");
        // Pattern-based allow list must remain unused so '*' cannot ever sneak
        // through with credentials enabled.
        assertThat(api.getAllowedOriginPatterns()).isNull();
    }

    @Test
    void dropsWildcardOriginWhileKeepingExplicitOnes() {
        CorsConfiguration api = apiCorsConfiguration("*," + LOCALHOST_VITE_ORIGIN);

        assertThat(api.getAllowedOrigins()).containsExactly(LOCALHOST_VITE_ORIGIN);
    }

    @Test
    void fallsBackToLocalDevOriginsWhenAllOriginsAreBlankOrWildcard() {
        CorsConfiguration api = apiCorsConfiguration("  ,  *  ,");

        assertThat(api.getAllowedOrigins()).containsExactlyElementsOf(CorsConfig.DEFAULT_LOCAL_DEV_ORIGINS);
    }

    @Test
    void trimsSurroundingWhitespaceOnOrigins() {
        CorsConfiguration api = apiCorsConfiguration("  " + LOCALHOST_VITE_ORIGIN + " , " + EXAMPLE_ORIGIN + "  ");

        assertThat(api.getAllowedOrigins())
                .containsExactly(LOCALHOST_VITE_ORIGIN, EXAMPLE_ORIGIN);
    }

    @ParameterizedTest
    @CsvSource({
            "https://example.test/app, path",
            "https://example.test?debug=true, query string",
            "https://example.test#fragment, fragment",
            "https://user:secret@example.test, embedded credentials",
            "https://example.test:65536, port",
            "https://example.test:0, port"
    })
    void rejectsAnInvalidConfiguredOrigin(String origin, String expectedMessage) {
        assertThatThrownBy(() -> corsConfigurationSource(config, origin))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    @Test
    void normalizesOriginsAndRemovesDuplicatesInConfiguredOrder() {
        CorsConfiguration api = apiCorsConfiguration(
                "  HTTPS://Example.Test:443,https://example.test,http://LOCALHOST:5173,http://example.test:80 ");

        assertThat(api.getAllowedOrigins())
                .containsExactly(EXAMPLE_ORIGIN, "http://localhost:5173", "http://example.test");
        assertThat(api.checkOrigin(EXAMPLE_ORIGIN)).isEqualTo(EXAMPLE_ORIGIN);
    }

    /** The fallback that applies when no origins are configured must only ever name this machine. */
    @Test
    void defaultLocalDevOriginsAreLoopbackOnly() {
        assertThat(CorsConfig.DEFAULT_LOCAL_DEV_ORIGINS)
                .contains(LOCALHOST_VITE_ORIGIN)
                .allMatch(origin -> origin.startsWith("http://localhost:") || origin.startsWith("http://127.0.0.1:"));
    }

    @Test
    void prodProfileRequiresExplicitCorsOrigins() {
        CorsConfig prodConfig = new CorsConfig(environment("prod"));

        assertThatThrownBy(() -> corsConfigurationSource(prodConfig, "  ,  *  ,"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_CORS_ALLOWED_ORIGINS");
    }

    @Test
    void prodProfileRequiresPublicHttpsCorsOrigins() {
        CorsConfig prodConfig = new CorsConfig(environment("prod"));

        assertThatThrownBy(() -> corsConfigurationSource(prodConfig, "http://app.example.test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTPS");
        assertThatThrownBy(() -> corsConfigurationSource(prodConfig, "https://localhost:5173"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("non-loopback public host");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://10.0.0.8",
            "https://[fd00::2]",
            "https://workstation.localhost",
            "https://0177.0.0.1",
            "https://012.0.0.1",
            "https://0x7f000001",
            "https://0.1.2.3",
            "https://100.64.0.1",
            "https://255.255.255.255",
            "https://[2001:db8::1]",
            "https://[::127.0.0.1]",
            "https://[::ffff:0:127.0.0.1]",
            "https://[2002:7f00:0001::1]"
    })
    void productionRefusesLocalDestinations(String origin) {
        CorsConfig prod = new CorsConfig(environment("production"));
        assertThatThrownBy(() -> corsConfigurationSource(prod, origin))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("public host");
    }

    @Test
    void aNumberedDnsLabelRemainsAHostName() {
        CorsConfig prod = new CorsConfig(environment("prod"));
        String origin = "https://127.campus.example.test";
        UrlBasedCorsConfigurationSource source = corsConfigurationSource(prod, origin);
        assertThat(source.getCorsConfigurations().get("/api/**").checkOrigin(origin)).isEqualTo(origin);
    }

    @Test
    void springWiredBeanAppliesProdProfileOriginRules() {
        new ApplicationContextRunner()
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues("app.cors.allowed-origins=" + LOCALHOST_VITE_ORIGIN)
                .withUserConfiguration(CorsConfig.class)
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .rootCause()
                        .hasMessageContaining("HTTPS"));
    }

    private CorsConfiguration apiCorsConfiguration(String allowedOrigins) {
        return corsConfigurationSource(config, allowedOrigins).getCorsConfigurations().get("/api/**");
    }

    private static UrlBasedCorsConfigurationSource corsConfigurationSource(CorsConfig target, String allowedOrigins) {
        return (UrlBasedCorsConfigurationSource) target.corsConfigurationSource(target.corsAllowedOrigins(allowedOrigins));
    }
}
