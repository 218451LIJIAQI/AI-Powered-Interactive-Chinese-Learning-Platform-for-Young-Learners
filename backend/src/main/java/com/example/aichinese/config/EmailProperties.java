package com.example.aichinese.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code app.email.*} configuration block.
 *
 * <p>Captures the enable flag, the {@code From:} header, and the
 * frontend password-reset base URL. Both string fields go through
 * defensive sanitisers / validators when accessed:</p>
 * <ul>
 *   <li>{@link #resolveFrom(String)} strips CR/LF from the address to
 *       prevent SMTP header injection (an attacker who can write the
 *       {@code from} property could otherwise inject a forged
 *       {@code Bcc:} or {@code To:} line).</li>
 *   <li>{@link #resolvePasswordResetBaseUrl()} requires a valid
 *       absolute HTTP(S) URL so the reset link generated for an email
 *       cannot accidentally be {@code javascript:} or relative.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        boolean enabled,
        String from,
        String passwordResetBaseUrl) {

    public String resolveFrom(String fallbackEmail) {
        if (from != null && !from.isBlank()) {
            return sanitizeHeaderValue(from, "Email from address");
        }
        if (fallbackEmail != null && !fallbackEmail.isBlank()) {
            return sanitizeHeaderValue(fallbackEmail, "SMTP username fallback email");
        }
        return "no-reply@example.com";
    }

    /**
     * Returns the base URL the frontend reset page is served from, with a
     * sensible local-development default. The token is always appended as
     * a {@code ?token=...} query string by the calling code.
     */
    public String resolvePasswordResetBaseUrl() {
        if (passwordResetBaseUrl != null && !passwordResetBaseUrl.isBlank()) {
            return validateHttpUrl(passwordResetBaseUrl.trim(), "Password reset base URL");
        }
        return "http://localhost:5173/reset-password";
    }

    private static String sanitizeHeaderValue(String value, String label) {
        String trimmed = value.trim();
        if (trimmed.indexOf('\r') >= 0 || trimmed.indexOf('\n') >= 0) {
            throw new IllegalArgumentException(label + " must not contain line breaks.");
        }
        return trimmed;
    }

    private static String validateHttpUrl(String value, String label) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException(label + " must be an absolute HTTP(S) URL.");
            }
            return value;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(label + " must be a valid absolute HTTP(S) URL.", exception);
        }
    }
}
