package com.example.aichinese.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SecurityPropertiesTests {

    @Test
    void normalizesSecretCookieNameAndSameSitePolicy() {
        SecurityProperties properties = new SecurityProperties(
                "  RuntimeStyleJwtSecretForConfigTests1234567890AB  ",
                900_000L,
                604_800_000L,
                "  mandarin_spark_refresh  ",
                false,
                "strict");

        assertThat(properties.secret()).isEqualTo("RuntimeStyleJwtSecretForConfigTests1234567890AB");
        assertThat(properties.refreshTokenCookieName()).isEqualTo("mandarin_spark_refresh");
        assertThat(properties.refreshTokenSameSite()).isEqualTo("Strict");
        assertThat(properties.toString()).doesNotContain(properties.secret());
    }

    @Test
    void rejectsInvalidRefreshCookieSettings() {
        assertThatThrownBy(() -> new SecurityProperties(
                "RuntimeStyleJwtSecretForConfigTests1234567890AB",
                900_000L,
                604_800_000L,
                "refresh cookie",
                false,
                "Strict"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cookie name");

        assertThatThrownBy(() -> new SecurityProperties(
                "RuntimeStyleJwtSecretForConfigTests1234567890AB",
                900_000L,
                604_800_000L,
                "mandarin_spark_refresh",
                false,
                "Loose"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SameSite");

        assertThatThrownBy(() -> new SecurityProperties(
                "RuntimeStyleJwtSecretForConfigTests1234567890AB",
                900_000L,
                604_800_000L,
                "mandarin_spark_refresh",
                false,
                "None"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Secure=true");
    }
}
