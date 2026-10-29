package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.aichinese.config.EmailProperties;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@code EmailProperties} - pins the
 * {@code resolveFrom} fallback chain (configured From to SMTP username),
 * trimming behaviour, and the password-reset URL contract.
 */
class EmailPropertiesTests {

    private static final String FALLBACK_FROM = "fallback@example.com";
    private static final String FROM_ADDRESS = "no-reply@example.com";
    private static final String PASSWORD_RESET_BASE_URL = "https://example.com/reset-password";
    private static final String LINE_BREAK_ERROR = "line breaks";
    private static final String PASSWORD_RESET_BASE_URL_ERROR = "Password reset base URL";

    @Test
    void resolvesTrimmedFromAndPasswordResetBaseUrl() {
        EmailProperties properties = new EmailProperties(
                true,
                "  " + FROM_ADDRESS + "  ",
                "  " + PASSWORD_RESET_BASE_URL + "  ");

        assertThat(properties.resolveFrom(FALLBACK_FROM)).isEqualTo(FROM_ADDRESS);
        assertThat(properties.resolvePasswordResetBaseUrl()).isEqualTo(PASSWORD_RESET_BASE_URL);
    }

    @Test
    void rejectsHeaderLineBreaksInConfiguredOrFallbackFromAddress() {
        assertInvalidFromAddress("safe@example.com\r\nBcc: attacker@example.com", FALLBACK_FROM);

        assertInvalidFromAddress(null, FALLBACK_FROM + "\nBcc: attacker@example.com");
    }

    @Test
    void rejectsNonHttpPasswordResetBaseUrl() {
        assertInvalidPasswordResetBaseUrl("javascript:alert(1)");

        assertInvalidPasswordResetBaseUrl("ftp://example.com/reset");
    }

    private static void assertInvalidFromAddress(String configuredFrom, String fallbackFrom) {
        assertThatThrownBy(() -> properties(configuredFrom, null).resolveFrom(fallbackFrom))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(LINE_BREAK_ERROR);
    }

    private static void assertInvalidPasswordResetBaseUrl(String passwordResetBaseUrl) {
        assertThatThrownBy(() -> properties(FROM_ADDRESS, passwordResetBaseUrl).resolvePasswordResetBaseUrl())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(PASSWORD_RESET_BASE_URL_ERROR);
    }

    private static EmailProperties properties(String fromAddress, String passwordResetBaseUrl) {
        return new EmailProperties(true, fromAddress, passwordResetBaseUrl);
    }
}
