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
                "strict");

        assertThat(properties.secret()).isEqualTo(SECRET);
        assertThat(properties.refreshTokenCookieName()).isEqualTo(REFRESH_COOKIE_NAME);
        assertThat(properties.refreshTokenSameSite()).isEqualTo("Strict");
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }

    @Test
    void rejectsInvalidRefreshCookieSettings() {
        assertInvalidRefreshCookieSettings("refresh cookie", "Strict", "cookie name");

        assertInvalidRefreshCookieSettings(REFRESH_COOKIE_NAME, "Loose", "SameSite");

        assertInvalidRefreshCookieSettings(REFRESH_COOKIE_NAME, "None", "Secure=true");
    }

    private static void assertInvalidRefreshCookieSettings(
            String refreshCookieName,
            String refreshTokenSameSite,
            String expectedMessage) {
        assertThatThrownBy(() -> securityProperties(SECRET, refreshCookieName, refreshTokenSameSite))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(expectedMessage);
    }

    /** Settings with an insecure refresh cookie, so {@code SameSite=None} is refused for lack of Secure. */
    private static SecurityProperties securityProperties(
            String secret,
            String refreshCookieName,
            String refreshTokenSameSite) {
        return new SecurityProperties(
                secret,
                ACCESS_TOKEN_EXPIRATION_MS,
                REFRESH_TOKEN_EXPIRATION_MS,
                refreshCookieName,
                false,
                refreshTokenSameSite);
    }
}
