package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email")
public record EmailProperties(
        boolean enabled,
        String from) {

    public String resolveFrom(String fallbackEmail) {
        if (from != null && !from.isBlank()) {
            return from.trim();
        }
        if (fallbackEmail != null && !fallbackEmail.isBlank()) {
            return fallbackEmail.trim();
        }
        return "no-reply@example.com";
    }
}
