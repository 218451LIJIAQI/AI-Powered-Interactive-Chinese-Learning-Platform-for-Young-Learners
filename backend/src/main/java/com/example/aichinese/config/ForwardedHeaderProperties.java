package com.example.aichinese.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for {@code app.security.forwarded.*}.
 *
 * <p>The single {@code trustedProxies} list is consumed by
 * {@link com.example.aichinese.security.ClientIpResolver} to decide
 * whether the inbound {@code X-Forwarded-For} header may be honoured -
 * see that class for the full trust model. Each entry is either a
 * literal IP address ({@code 10.0.0.1}, {@code [::1]}) or a CIDR range
 * ({@code 10.0.0.0/8}, {@code 2001:db8::/32}).</p>
 *
 * <p>The compact constructor null-coalesces the supplied list, drops
 * blank entries, and trims whitespace so the runtime resolver always
 * sees a clean canonical list - eliminating the need for defensive
 * null / blank checks at every call site.</p>
 */
@ConfigurationProperties(prefix = "app.security.forwarded")
public record ForwardedHeaderProperties(List<String> trustedProxies) {

    public ForwardedHeaderProperties {
        trustedProxies = trustedProxies == null
                ? List.of()
                : trustedProxies.stream()
                        .filter(value -> value != null && !value.isBlank())
                        // strip() is Unicode-aware so a stray CJK fullwidth space
                        // pasted accidentally from a CJK editor cannot survive
                        // canonicalisation and silently de-match the trust list.
                        .map(String::strip)
                        .toList();
    }
}
