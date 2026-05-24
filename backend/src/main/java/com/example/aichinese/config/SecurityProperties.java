package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
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
        long expiration) {
}
