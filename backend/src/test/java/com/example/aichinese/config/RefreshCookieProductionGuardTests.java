package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RefreshCookieProductionGuardTests {

    private static final String SECRET = "8a5a9ccb6ee95cf946daa7df94ed9e6527edcad1072366c70d73fb686c504573";
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 3_600_000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 604_800_000L;

    @Test
    void rejectsInsecureRefreshCookieInProduction() {
        assertThatThrownBy(() -> createGuard(environmentWithProfile("prod"), false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JWT_REFRESH_COOKIE_SECURE");
    }

    @Test
    void rejectsInsecureRefreshCookieForProductionAlias() {
        assertThatThrownBy(() -> createGuard(environmentWithProfile("production"), false))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsSecureRefreshCookieInProduction() {
        assertThatCode(() -> createGuard(environmentWithProfile("prod"), true))
                .doesNotThrowAnyException();
    }

    @Test
    void localAndDemoProfilesKeepInsecureHttpDefault() {
        assertThatCode(() -> createGuard(new MockEnvironment(), false)).doesNotThrowAnyException();
        assertThatCode(() -> createGuard(environmentWithProfile("local"), false)).doesNotThrowAnyException();
        assertThatCode(() -> createGuard(environmentWithProfile("demo"), false)).doesNotThrowAnyException();
    }

    private static RefreshCookieProductionGuard createGuard(MockEnvironment environment, boolean secure) {
        SecurityProperties properties = new SecurityProperties(
                SECRET,
                ACCESS_TOKEN_EXPIRATION_MS,
                REFRESH_TOKEN_EXPIRATION_MS,
                "mandarin_spark_refresh",
                secure,
                "Strict");
        return new RefreshCookieProductionGuard(environment, properties);
    }

    private static MockEnvironment environmentWithProfile(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
