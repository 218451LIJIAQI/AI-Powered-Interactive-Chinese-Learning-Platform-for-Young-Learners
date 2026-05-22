package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.example.aichinese.config.EmailProperties;

class EmailPropertiesTests {

    @Test
    void resolvesTrimmedFromAndPasswordResetBaseUrl() {
        EmailProperties properties = new EmailProperties(
                true,
                "  no-reply@example.com  ",
                "  https://example.com/reset-password  ");

        assertThat(properties.resolveFrom("fallback@example.com")).isEqualTo("no-reply@example.com");
        assertThat(properties.resolvePasswordResetBaseUrl()).isEqualTo("https://example.com/reset-password");
    }

    @Test
    void rejectsHeaderLineBreaksInConfiguredOrFallbackFromAddress() {
        assertThatThrownBy(() -> new EmailProperties(true, "safe@example.com\r\nBcc: attacker@example.com", null)
                .resolveFrom("fallback@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line breaks");

        assertThatThrownBy(() -> new EmailProperties(true, null, null)
                .resolveFrom("fallback@example.com\nBcc: attacker@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("line breaks");
    }

    @Test
    void rejectsNonHttpPasswordResetBaseUrl() {
        assertThatThrownBy(() -> new EmailProperties(true, "no-reply@example.com", "javascript:alert(1)")
                .resolvePasswordResetBaseUrl())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password reset base URL");

        assertThatThrownBy(() -> new EmailProperties(true, "no-reply@example.com", "ftp://example.com/reset")
                .resolvePasswordResetBaseUrl())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Password reset base URL");
    }
}
