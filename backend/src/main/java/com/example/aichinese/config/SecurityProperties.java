package com.example.aichinese.config;

import java.util.Locale;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Strongly typed binding for the {@code app.security.jwt.*} configuration block.
 */
@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record SecurityProperties(
        @NotBlank(message = "JWT secret must not be blank.")
        String secret,
        @Positive(message = "JWT expiration must be greater than zero.")
        long expiration,
        @Positive(message = "Refresh token expiration must be greater than zero.")
        long refreshTokenExpiration,
        @NotBlank(message = "Refresh token cookie name must not be blank.")
        String refreshTokenCookieName,
        boolean refreshTokenCookieSecure,
        @NotBlank(message = "Refresh token SameSite policy must not be blank.")
        String refreshTokenSameSite) {

    @ConstructorBinding
    public SecurityProperties {
        secret = normalizeSecret(secret);
        if (expiration <= 0) {
            throw new IllegalArgumentException("JWT expiration must be greater than zero.");
        }
        if (refreshTokenExpiration <= 0) {
            throw new IllegalArgumentException("Refresh token expiration must be greater than zero.");
        }
        refreshTokenCookieName = requireCookieName(refreshTokenCookieName);
        refreshTokenSameSite = normalizeSameSite(refreshTokenSameSite);
        if ("None".equals(refreshTokenSameSite) && !refreshTokenCookieSecure) {
            throw new IllegalArgumentException("Refresh token cookies with SameSite=None must also set Secure=true.");
        }
    }

    public SecurityProperties(String secret, long expiration) {
        this(secret, expiration, 604_800_000L, "mandarin_spark_refresh", false, "Strict");
    }

    @Override
    public String toString() {
        return "SecurityProperties[secret=" + maskedSecret()
                + ", expiration=" + expiration + "ms"
                + ", refreshTokenExpiration=" + refreshTokenExpiration + "ms"
                + ", refreshTokenCookieName=" + refreshTokenCookieName
                + ", refreshTokenCookieSecure=" + refreshTokenCookieSecure
                + ", refreshTokenSameSite=" + refreshTokenSameSite + "]";
    }

    private String maskedSecret() {
        if (secret == null || secret.isBlank()) {
            return "[unset]";
        }
        if (secret.startsWith("base64:")) {
            return "[set:base64]";
        }
        return "[set]";
    }

    private static String normalizeSecret(String value) {
        return value == null ? null : value.strip();
    }

    private static String requireCookieName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refresh token cookie name must not be blank.");
        }
        String normalized = value.strip();
        for (int i = 0; i < normalized.length(); i++) {
            char ch = normalized.charAt(i);
            if (!isCookieTokenCharacter(ch)) {
                throw new IllegalArgumentException("Refresh token cookie name contains an invalid character.");
            }
        }
        return normalized;
    }

    private static boolean isCookieTokenCharacter(char ch) {
        return ch == '!' || ch == '#' || ch == '$' || ch == '%' || ch == '&' || ch == '\''
                || ch == '*' || ch == '+' || ch == '-' || ch == '.' || ch == '^' || ch == '_'
                || ch == '`' || ch == '|' || ch == '~'
                || (ch >= '0' && ch <= '9')
                || (ch >= 'A' && ch <= 'Z')
                || (ch >= 'a' && ch <= 'z');
    }

    private static String normalizeSameSite(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refresh token SameSite policy must not be blank.");
        }
        return switch (value.strip().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "lax" -> "Lax";
            case "none" -> "None";
            default -> throw new IllegalArgumentException("Refresh token SameSite policy must be Strict, Lax, or None.");
        };
    }
}
