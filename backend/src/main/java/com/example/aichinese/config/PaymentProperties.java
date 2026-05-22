package com.example.aichinese.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        String successUrl,
        String cancelUrl,
        boolean mockSuccessEnabled,
        String provider,
        String stripeApiBaseUrl,
        String stripeApiKey,
        String stripeWebhookSecret) {

    private static final Set<String> ALLOWED_PROVIDERS = Set.of("mock", "no-op", "stripe");

    public PaymentProperties {
        successUrl = validateUrl(normalize(successUrl, "http://localhost:5173/payment/success"), "Payment success URL");
        cancelUrl = validateUrl(normalize(cancelUrl, "http://localhost:5173/payment/cancel"), "Payment cancel URL");
        provider = normalizeProvider(provider);
        stripeApiBaseUrl = validateUrl(normalize(stripeApiBaseUrl, "https://api.stripe.com"), "Stripe API base URL");
        stripeApiKey = stripeApiKey == null ? "" : stripeApiKey.trim();
        stripeWebhookSecret = stripeWebhookSecret == null ? "" : stripeWebhookSecret.trim();
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String validateUrl(String value, String label) {
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

    private static String normalizeProvider(String value) {
        String normalized = normalize(value, "no-op").toLowerCase(Locale.ROOT);
        if ("noop".equals(normalized)) {
            normalized = "no-op";
        }
        if (!ALLOWED_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException("Payment provider must be one of: mock, no-op, stripe.");
        }
        return normalized;
    }
}
