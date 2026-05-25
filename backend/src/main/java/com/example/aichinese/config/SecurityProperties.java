package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

/**
 * Strongly-typed binding for the {@code app.security.jwt.*} configuration block.
 *
 * <p>Carries the two settings that govern the JWT bearer-token format
 * the platform uses for authentication: the signing secret (consumed
 * by {@link com.example.aichinese.security.JwtService}) and the
 * token validity duration in milliseconds.</p>
 *
 * <p>{@link Validated @Validated} + Bean Validation annotations enforce
 * the contract at startup: a missing / blank secret or a non-positive
 * expiry fails fast before any traffic is served. Additional
 * format-level validation (minimum entropy, {@code base64:} prefix
 * handling, etc.) lives in {@code JwtService} so the runtime can issue
 * a more descriptive error than Bean Validation supports out of the box.</p>
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
    }

    public SecurityProperties(String secret, long expiration) {
        this(secret, expiration, 604_800_000L, "mandarin_spark_refresh", false, "Strict");
    }

    /**
     * Diagnostic representation that masks the signing secret. The
     * auto-generated record toString would dump it verbatim; a stray
     * {@code log.info(securityProperties)} that shipped to prod would
     * then leak a value with the same authority as the entire user
     * table (anyone with the secret can mint admin tokens). We report
     * only presence flag plus the {@code base64:} prefix marker when
     * present, so ops can still verify the secret is configured without
     * any information leak.
     */
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
}
