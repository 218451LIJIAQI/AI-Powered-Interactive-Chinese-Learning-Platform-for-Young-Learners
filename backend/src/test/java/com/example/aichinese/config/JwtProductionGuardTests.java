package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtProductionGuardTests {

    @Test
    void rejectsPlaceholderSecretInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtProductionGuard(
                environment,
                new SecurityProperties("replace-with-a-unique-random-secret-at-least-32-characters-long", 3_600_000)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void allowsNonPlaceholderSecretInProduction() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatCode(() -> new JwtProductionGuard(
                environment,
                new SecurityProperties("8a5a9ccb6ee95cf946daa7df94ed9e6527edcad1072366c70d73fb686c504573", 3_600_000)))
                .doesNotThrowAnyException();
    }

    @Test
    void localExampleMayUsePlaceholderUntilDeveloperConfiguresCredentials() {
        assertThatCode(() -> new JwtProductionGuard(
                new MockEnvironment(),
                new SecurityProperties("replace-with-a-strong-plain-text-secret-at-least-32-characters-long", 3_600_000)))
                .doesNotThrowAnyException();
    }
}
