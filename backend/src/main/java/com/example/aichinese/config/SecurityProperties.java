package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record SecurityProperties(
        @NotBlank(message = "JWT secret must not be blank.")
        String secret,
        @Positive(message = "JWT expiration must be greater than zero.")
        long expiration) {
}
