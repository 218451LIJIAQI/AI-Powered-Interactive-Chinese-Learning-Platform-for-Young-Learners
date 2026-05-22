package com.example.aichinese.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class PaymentProductionGuard {

    private final Environment environment;
    private final PaymentProperties paymentProperties;

    public PaymentProductionGuard(Environment environment, PaymentProperties paymentProperties) {
        this.environment = environment;
        this.paymentProperties = paymentProperties;
        validate();
    }

    void validate() {
        if (!isProductionProfileActive()) {
            return;
        }

        if (paymentProperties.mockSuccessEnabled()) {
            throw new IllegalStateException(
                    "PAYMENT_MOCK_SUCCESS_ENABLED must be false when the prod profile is active.");
        }
        if (!"stripe".equals(paymentProperties.provider())) {
            throw new IllegalStateException(
                    "PAYMENT_PROVIDER must be set to 'stripe' when the prod profile is active.");
        }
        if (paymentProperties.stripeApiKey() == null || paymentProperties.stripeApiKey().isBlank()) {
            throw new IllegalStateException(
                    "STRIPE_API_KEY must be configured when PAYMENT_PROVIDER=stripe and the prod profile is active.");
        }
        if (paymentProperties.stripeWebhookSecret() == null || paymentProperties.stripeWebhookSecret().isBlank()) {
            throw new IllegalStateException(
                    "STRIPE_WEBHOOK_SECRET must be configured when PAYMENT_PROVIDER=stripe and the prod profile is active.");
        }

        requireProductionReadyUrl(paymentProperties.successUrl(), "PAYMENT_SUCCESS_URL");
        requireProductionReadyUrl(paymentProperties.cancelUrl(), "PAYMENT_CANCEL_URL");
        requireProductionReadyUrl(paymentProperties.stripeApiBaseUrl(), "STRIPE_API_BASE_URL");
    }

    private boolean isProductionProfileActive() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(String::trim)
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(profile -> profile.equals("prod") || profile.equals("production"));
    }

    private void requireProductionReadyUrl(String value, String label) {
        URI uri = URI.create(value);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().trim().toLowerCase(Locale.ROOT);
        String host = normalizeHost(uri.getHost());

        if (!"https".equals(scheme)) {
            throw new IllegalStateException(label + " must use HTTPS when the prod profile is active.");
        }
        if (host.isBlank() || isLocalHost(host)) {
            throw new IllegalStateException(
                    label + " must target a non-loopback public host when the prod profile is active.");
        }
    }

    private String normalizeHost(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]") && normalized.length() > 2) {
            return normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private boolean isLocalHost(String host) {
        return "localhost".equals(host)
                || "0.0.0.0".equals(host)
                || "::1".equals(host)
                || host.startsWith("127.");
    }
}
