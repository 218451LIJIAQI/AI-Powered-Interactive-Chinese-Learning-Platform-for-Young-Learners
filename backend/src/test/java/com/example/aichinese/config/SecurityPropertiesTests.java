package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityPropertiesTests {

    private static final String SECRET = "RuntimeStyleJwtSecretForConfigTests1234567890AB";
    private static final String REFRESH_COOKIE_NAME = "mandarin_spark_refresh";
    private static final long ACCESS_TOKEN_EXPIRATION_MS = 900_000L;
    private static final long REFRESH_TOKEN_EXPIRATION_MS = 604_800_000L;

    @Test
    void normalizesSecretCookieNameAndSameSitePolicy() {
        SecurityProperties properties = securityProperties(
                "  " + SECRET + "  ",
                "  " + REFRESH_COOKIE_NAME + "  ",
                false,
                "strict");

        assertThat(properties.secret()).isEqualTo(SECRET);
        assertThat(properties.refreshTokenCookieName()).isEqualTo(REFRESH_COOKIE_NAME);
        assertThat(properties.refreshTokenSameSite()).isEqualTo("Strict");
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }

    @Test
    void rejectsInvalidRefreshCookieSettings() {
        assertInvalidRefreshCookieSettings("refresh cookie", false, "Strict", "cookie name");

        assertInvalidRefreshCookieSettings(REFRESH_COOKIE_NAME, false, "Loose", "SameSite");

        assertInvalidRefreshCookieSettings(REFRESH_COOKIE_NAME, false, "None", "Secure=true");
    }

    private static void assertInvalidRefreshCookieSettings(
            String refreshCookieName,
            boolean refreshCookieSecure,
            String refreshTokenSameSite,
            String expectedMessage) {
        assertThatThrownBy(() -> securityProperties(refreshCookieName, refreshCookieSecure, refreshTokenSameSite))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static SecurityProperties securityProperties(
            String refreshCookieName,
            boolean refreshCookieSecure,
            String refreshTokenSameSite) {
        return securityProperties(SECRET, refreshCookieName, refreshCookieSecure, refreshTokenSameSite);
    }

    private static SecurityProperties securityProperties(
            String secret,
            String refreshCookieName,
            boolean refreshCookieSecure,
            String refreshTokenSameSite) {
        return new SecurityProperties(
                secret,
                ACCESS_TOKEN_EXPIRATION_MS,
                REFRESH_TOKEN_EXPIRATION_MS,
                refreshCookieName,
                refreshCookieSecure,
                refreshTokenSameSite);
    }
}
