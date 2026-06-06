package com.example.aichinese.config;

import java.util.Locale;
import java.util.List;

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

    private static final List<String> ALLOWED_PROVIDERS = List.of("mock", "no-op", "stripe");
    private static final int MAX_URL_LENGTH = 2048;
    private static final String ALLOWED_PROVIDER_MESSAGE =
            "Payment provider must be one of: " + String.join(", ", ALLOWED_PROVIDERS) + ".";
    private static final String DEFAULT_SUCCESS_URL = "http://localhost:5174/payment/success";
    private static final String DEFAULT_CANCEL_URL = "http://localhost:5174/payment/cancel";
    private static final String DEFAULT_STRIPE_API_BASE_URL = "https://api.stripe.com";
    private static final String DEFAULT_PROVIDER = "no-op";

    public PaymentProperties {
        successUrl = validateUrl(normalize(successUrl, DEFAULT_SUCCESS_URL), "Payment success URL");
        cancelUrl = validateUrl(normalize(cancelUrl, DEFAULT_CANCEL_URL), "Payment cancel URL");
        provider = normalizeProvider(provider);
        stripeApiBaseUrl = validateUrl(normalize(stripeApiBaseUrl, DEFAULT_STRIPE_API_BASE_URL), "Stripe API base URL");
        stripeApiKey = stripeApiKey == null ? "" : stripeApiKey.strip();
        stripeWebhookSecret = stripeWebhookSecret == null ? "" : stripeWebhookSecret.strip();
    }

    private static String normalize(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.strip();
    }

    private static String validateUrl(String value, String label) {
        if (value.length() > MAX_URL_LENGTH) {
            throw new IllegalArgumentException(
                    label + " must be at most " + MAX_URL_LENGTH + " characters.");
        }
        HttpUrlValidation.requireAbsoluteHttpUrl(value, label);
        return value;
    }

    private static String normalizeProvider(String value) {
        String normalized = normalize(value, DEFAULT_PROVIDER).toLowerCase(Locale.ROOT);
        if ("noop".equals(normalized)) {
            normalized = "no-op";
        }
        if (!ALLOWED_PROVIDERS.contains(normalized)) {
            throw new IllegalArgumentException(ALLOWED_PROVIDER_MESSAGE);
        }
        return normalized;
    }

    @Override
    public String toString() {
        return "PaymentProperties[successUrl=" + successUrl
                + ", cancelUrl=" + cancelUrl
                + ", mockSuccessEnabled=" + mockSuccessEnabled
                + ", provider=" + provider
                + ", stripeApiBaseUrl=" + stripeApiBaseUrl
                + ", stripeApiKey=" + maskedStripeApiKey()
                + ", stripeWebhookSecret=" + stripeWebhookSecretStatus()
                + "]";
    }

    private String stripeWebhookSecretStatus() {
        return stripeWebhookSecret.isBlank() ? "[unset]" : "[set]";
    }

    private String maskedStripeApiKey() {
        if (stripeApiKey.isBlank()) {
            return "[unset]";
        }
        if (stripeApiKey.startsWith("sk_live_")) {
            return "[set:sk_live_***]";
        }
        if (stripeApiKey.startsWith("rk_live_")) {
            return "[set:rk_live_***]";
        }
        if (stripeApiKey.startsWith("sk_test_")) {
            return "[set:sk_test_***]";
        }
        if (stripeApiKey.startsWith("rk_test_")) {
            return "[set:rk_test_***]";
        }
        return "[set]";
    }
}
