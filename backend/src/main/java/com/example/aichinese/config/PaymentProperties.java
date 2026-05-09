package com.example.aichinese.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        String provider,
        String stripeSecretKey,
        String stripePublicKey,
        String stripeWebhookSecret,
        String successUrl,
        String cancelUrl,
        String currency,
        Integer timeoutSeconds,
        boolean enableRealProvider,
        boolean mockSuccessEnabled) {

    public PaymentProperties {
        provider = normalize(provider, "stripe");
        successUrl = normalize(successUrl, "http://localhost:5173/payment/success");
        cancelUrl = normalize(cancelUrl, "http://localhost:5173/payment/cancel");
        currency = normalize(currency, "MYR").toUpperCase(java.util.Locale.ROOT);
        timeoutSeconds = timeoutSeconds == null || timeoutSeconds <= 0 ? 30 : timeoutSeconds;
    }

    public boolean hasStripeSecretKey() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    public boolean hasStripeWebhookSecret() {
        return stripeWebhookSecret != null && !stripeWebhookSecret.isBlank();
    }

    public boolean isAvailableForRealCheckout() {
        return enableRealProvider && hasStripeSecretKey();
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }
}
