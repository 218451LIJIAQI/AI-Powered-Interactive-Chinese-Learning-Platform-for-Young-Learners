package com.example.aichinese.config;

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

    private static final String DEFAULT_FROM = "no-reply@example.com";
    private static final String DEFAULT_RESET_URL = "http://localhost:5173/reset-password";

    /**
     * Practical caps so a degenerate config string cannot inflate the SMTP
     * header / outbound URL length without bound. RFC 5321 limits header
     * lines to 998 octets; we cap inputs well below that to leave room for
     * the {@code From: } prefix, display-name, and angle brackets.
     */
    private static final int MAX_FROM_LENGTH = 256;
    private static final int MAX_URL_LENGTH = 2048;

    public String resolveFrom(String fallbackEmail) {
        if (from != null && !from.isBlank()) {
            return sanitizeHeaderValue(from, "Email from address");
        }
        if (fallbackEmail != null && !fallbackEmail.isBlank()) {
            return sanitizeHeaderValue(fallbackEmail, "SMTP username fallback email");
        }
        return DEFAULT_FROM;
    }

    /**
     * Returns the base URL the frontend reset page is served from, with a
     * sensible local-development default. The token is always appended as
     * a {@code ?token=...} query string by the calling code.
     */
    public String resolvePasswordResetBaseUrl() {
        if (passwordResetBaseUrl != null && !passwordResetBaseUrl.isBlank()) {
            return validateHttpUrl(passwordResetBaseUrl.strip(), "Password reset base URL");
        }
        return DEFAULT_RESET_URL;
    }

    /**
     * Strips header-injection vectors from the supplied value:
     * <ul>
     *   <li>CR/LF \u2014 the classic SMTP {@code Bcc:}-injection vector,
     *       so we report it with the specific "line breaks" message that
     *       ops dashboards already key off.</li>
     *   <li>Any other ASCII C0 control character (0x00\u20130x1F minus
     *       horizontal tab, which is permitted by RFC 5322) plus DEL
     *       (0x7F). These are not strictly required by the classic
     *       attack but mail-server quirks have historically allowed
     *       NUL / vertical-tab to be reinterpreted as separators.</li>
     *   <li>Length cap to keep the resulting header within RFC 5321's
     *       998-octet line limit even after the {@code From: } prefix
     *       and quoted display-name brackets are added.</li>
     * </ul>
     */
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
