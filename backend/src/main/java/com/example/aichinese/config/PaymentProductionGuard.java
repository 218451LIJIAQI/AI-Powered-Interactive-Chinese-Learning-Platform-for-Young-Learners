package com.example.aichinese.config;

import java.util.Locale;
import java.util.Objects;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class PaymentProductionGuard {

    private static final String STRIPE_API_HOST = "api.stripe.com";
    private static final String STRIPE_PROVIDER = "stripe";
    private static final String PROD_CONTEXT = "when the prod profile is active";
    private static final Profiles PROD_PROFILES = Profiles.of("prod", "production");

    private final Environment environment;
    private final PaymentProperties paymentProperties;

    public PaymentProductionGuard(Environment environment, PaymentProperties paymentProperties) {
        this.environment = Objects.requireNonNull(environment, "environment");
        this.paymentProperties = Objects.requireNonNull(paymentProperties, "paymentProperties");
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
        if (!STRIPE_PROVIDER.equals(normalize(paymentProperties.provider()))) {
            throw new IllegalStateException(
                    "PAYMENT_PROVIDER must be set to 'stripe' when the prod profile is active.");
        }
        if (isBlank(paymentProperties.stripeApiKey())) {
            throw new IllegalStateException(
                    "STRIPE_API_KEY must be configured when PAYMENT_PROVIDER=stripe and the prod profile is active.");
        }
        requireLiveStripeApiKey(paymentProperties.stripeApiKey());
        if (isBlank(paymentProperties.stripeWebhookSecret())) {
            throw new IllegalStateException(
                    "STRIPE_WEBHOOK_SECRET must be configured when PAYMENT_PROVIDER=stripe and the prod profile is active.");
        }
        requireStripeWebhookSecret(paymentProperties.stripeWebhookSecret());

        requireProductionReadyUrl(paymentProperties.successUrl(), "PAYMENT_SUCCESS_URL");
        requireProductionReadyUrl(paymentProperties.cancelUrl(), "PAYMENT_CANCEL_URL");
        requireOfficialStripeApiBaseUrl(paymentProperties.stripeApiBaseUrl());
    }

    private boolean isProductionProfileActive() {
        return environment.acceptsProfiles(PROD_PROFILES);
    }

    private static void requireProductionReadyUrl(String value, String label) {
        HttpUrlValidation.requirePublicHttpsUrl(value, label, PROD_CONTEXT);
    }

    private static void requireOfficialStripeApiBaseUrl(String value) {
        var uri = HttpUrlValidation.requirePublicHttpsUrl(value, "STRIPE_API_BASE_URL", PROD_CONTEXT);
        String host = HttpUrlValidation.normalizeHost(uri.getHost());
        if (!STRIPE_API_HOST.equals(host)) {
            throw new IllegalStateException(
                    "STRIPE_API_BASE_URL must be https://api.stripe.com when the prod profile is active.");
        }
    }

    private static void requireLiveStripeApiKey(String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("sk_test_") || trimmed.startsWith("rk_test_")) {
            throw new IllegalStateException(
                    "STRIPE_API_KEY must be a live-mode Stripe secret key when the prod profile is active.");
        }
        if (!(trimmed.startsWith("sk_live_") || trimmed.startsWith("rk_live_"))) {
            throw new IllegalStateException(
                    "STRIPE_API_KEY must start with sk_live_ or rk_live_ when the prod profile is active.");
        }
    }

    private static void requireStripeWebhookSecret(String value) {
        if (!value.trim().startsWith("whsec_")) {
            throw new IllegalStateException(
                    "STRIPE_WEBHOOK_SECRET must be a Stripe webhook signing secret when the prod profile is active.");
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
