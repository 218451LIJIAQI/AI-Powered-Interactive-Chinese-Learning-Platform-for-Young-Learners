package com.example.aichinese.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.forwarded")
public record ForwardedHeaderProperties(List<String> trustedProxies) {

    public ForwardedHeaderProperties {
        trustedProxies = trustedProxies == null
                ? List.of()
                : trustedProxies.stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
    }
}
