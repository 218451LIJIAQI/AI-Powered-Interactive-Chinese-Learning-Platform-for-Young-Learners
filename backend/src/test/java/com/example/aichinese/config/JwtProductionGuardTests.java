package com.example.aichinese.config;

import static com.example.aichinese.support.ConfigFixtures.environment;
import static com.example.aichinese.support.ConfigFixtures.securityProperties;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class JwtProductionGuardTests {

    private static final String CONFIGURED_SECRET = "8a5a9ccb6ee95cf946daa7df94ed9e6527edcad1072366c70d73fb686c504573";

    @Test
    void rejectsPlaceholderSecretInProduction() {
        assertThatThrownBy(() -> createGuard(environment("prod"),
                "replace-with-a-unique-random-secret-at-least-32-characters-long", true))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void allowsNonPlaceholderSecretInProduction() {
        assertThatCode(() -> createGuard(environment("prod"), CONFIGURED_SECRET, true))
                .doesNotThrowAnyException();
    }

    @Test
    void localExampleMayUsePlaceholderUntilDeveloperConfiguresCredentials() {
        assertThatCode(() -> createGuard(environment(),
                "replace-with-a-strong-plain-text-secret-at-least-32-characters-long", false))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void rejectsInsecureRefreshCookieInProductionAndItsAlias(String productionProfile) {
        assertThatThrownBy(() -> createGuard(environment(productionProfile), CONFIGURED_SECRET, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_REFRESH_COOKIE_SECURE");
    }

    @Test
    void localAndDemoProfilesKeepInsecureHttpDefault() {
        assertThatCode(() -> createGuard(environment(), CONFIGURED_SECRET, false)).doesNotThrowAnyException();
        assertThatCode(() -> createGuard(environment("local"), CONFIGURED_SECRET, false)).doesNotThrowAnyException();
        assertThatCode(() -> createGuard(environment("demo"), CONFIGURED_SECRET, false)).doesNotThrowAnyException();
    }

    private static JwtProductionGuard createGuard(MockEnvironment environment, String secret, boolean secure) {
        return new JwtProductionGuard(environment, securityProperties(secret, 3_600_000L, secure));
    }
}
