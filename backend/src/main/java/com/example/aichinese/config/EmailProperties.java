package com.example.aichinese.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        boolean enabled,
        String from,
        String passwordResetBaseUrl) {

    private static final String DEFAULT_FROM = "no-reply@example.com";
    private static final String DEFAULT_RESET_URL = "http://localhost:5174/reset-password";

    private static final int MAX_FROM_LENGTH = 256;
    private static final int MAX_URL_LENGTH = 2048;

    public String resolveFrom(String fallbackEmail) {
        if (hasText(from)) {
            return sanitizeHeaderValue(from, "Email from address");
        }
        if (hasText(fallbackEmail)) {
            return sanitizeHeaderValue(fallbackEmail, "SMTP username fallback email");
        }
        return DEFAULT_FROM;
    }

    public String resolvePasswordResetBaseUrl() {
        if (hasText(passwordResetBaseUrl)) {
            return validateHttpUrl(passwordResetBaseUrl.trim(), "Password reset base URL");
        }
        return DEFAULT_RESET_URL;
    }

    /**
     * A page of the web app next to the reset page (the reset URL is the one configured link to the frontend), e.g.
     * {@code confirm-email} for the e-mail change link.
     */
    public String resolveFrontendUrl(String page) {
        return URI.create(resolvePasswordResetBaseUrl()).resolve(page).toString();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String sanitizeHeaderValue(String value, String label) {
        String trimmed = value.trim();
        if (trimmed.length() > MAX_FROM_LENGTH) {
            throw new IllegalArgumentException(
                    label + " must be at most " + MAX_FROM_LENGTH + " characters.");
        }
        if (trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(label + " must not contain line breaks.");
        }
        for (int i = 0, length = trimmed.length(); i < length; i++) {
            char ch = trimmed.charAt(i);
            if ((ch < 0x20 && ch != '\t') || ch == 0x7F) {
                throw new IllegalArgumentException(label + " must not contain control characters.");
            }
        }
        return trimmed;
    }

    private static String validateHttpUrl(String value, String label) {
        if (value.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(
                    label + " must be at most " + MAX_URL_LENGTH + " characters.");
        }
        HttpUrlValidation.requireAbsoluteHttpUrl(value, label);
        return value;
    }
}
