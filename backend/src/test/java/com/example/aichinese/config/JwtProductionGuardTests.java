package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtProductionGuardTests {

    private static final long ACCESS_TOKEN_EXPIRATION_MS = 3_600_000;
    private static final String PRODUCTION_PROFILE = "prod";
    private static final String PRODUCTION_PLACEHOLDER_SECRET =
            "replace-with-a-unique-random-secret-at-least-32-characters-long";
    private static final String LOCAL_PLACEHOLDER_SECRET =
            "replace-with-a-strong-plain-text-secret-at-least-32-characters-long";
    private static final String STRONG_SECRET =
            "8a5a9ccb6ee95cf946daa7df94ed9e6527edcad1072366c70d73fb686c504573";

    @Test
    void rejectsPlaceholderSecretInProduction() {
        assertThatThrownBy(() -> createGuard(productionEnvironment(), PRODUCTION_PLACEHOLDER_SECRET))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_SECRET");
    }

    @Test
    void allowsNonPlaceholderSecretInProduction() {
        assertThatCode(() -> createGuard(productionEnvironment(), STRONG_SECRET))
                .doesNotThrowAnyException();
    }

    @Test
    void localExampleMayUsePlaceholderUntilDeveloperConfiguresCredentials() {
        assertThatCode(() -> createGuard(new MockEnvironment(), LOCAL_PLACEHOLDER_SECRET))
                .doesNotThrowAnyException();
    }

    private static JwtProductionGuard createGuard(MockEnvironment environment, String secret) {
        return new JwtProductionGuard(environment, new SecurityProperties(secret, ACCESS_TOKEN_EXPIRATION_MS));
    }

    private static MockEnvironment productionEnvironment() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(PRODUCTION_PROFILE);
        return environment;
    }
}
