package com.example.aichinese.config;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Fail-fast startup guard for the payment integration.
 *
 * <p>When the {@code prod} profile is active, this guard refuses to
 * launch the application unless every payment-related setting is
 * production-grade:</p>
 * <ul>
 *   <li>Mock-success mode is OFF — otherwise checkouts would silently
 *       complete without any real money movement.</li>
 *   <li>Provider is set to {@code stripe} — the only supported real
 *       provider; the {@code mock} / {@code no-op} variants are dev-only.</li>
 *   <li>Stripe API key is present and starts with {@code sk_live_} or
 *       {@code rk_live_} — refusing test-mode keys
 *       ({@code sk_test_} / {@code rk_test_}) prevents the production
 *       deploy from accidentally using a sandbox account.</li>
 *   <li>Stripe webhook signing secret is present and starts with
 *       {@code whsec_} — verified at construction so the signature
 *       check in {@code StripeWebhookService} cannot silently no-op.</li>
 *   <li>Success / cancel URLs are HTTPS and point to non-loopback
 *       hosts.</li>
 *   <li>Stripe API base URL is exactly {@code https://api.stripe.com}
 *       — pinning the hostname so a typo in
 *       {@code STRIPE_API_BASE_URL} cannot send live payment traffic
 *       to an attacker-controlled endpoint.</li>
 * </ul>
 *
 * <p>The constructor invokes {@link #validate()} so the guard runs at
 * bean creation time and throws {@link IllegalStateException} on
 * failure — Spring Boot stops the application on a constructor
 * failure, which is the desired fail-closed behaviour for any payment
 * misconfiguration.</p>
 */
@Component
public class PaymentProductionGuard {

    private static final String STRIPE_API_HOST = "api.stripe.com";

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
        requireLiveStripeApiKey(paymentProperties.stripeApiKey());
        if (paymentProperties.stripeWebhookSecret() == null || paymentProperties.stripeWebhookSecret().isBlank()) {
            throw new IllegalStateException(
                    "STRIPE_WEBHOOK_SECRET must be configured when PAYMENT_PROVIDER=stripe and the prod profile is active.");
        }
        requireStripeWebhookSecret(paymentProperties.stripeWebhookSecret());

        requireProductionReadyUrl(paymentProperties.successUrl(), "PAYMENT_SUCCESS_URL");
        requireProductionReadyUrl(paymentProperties.cancelUrl(), "PAYMENT_CANCEL_URL");
        requireOfficialStripeApiBaseUrl(paymentProperties.stripeApiBaseUrl());
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

    private void requireOfficialStripeApiBaseUrl(String value) {
        URI uri = URI.create(value);
        requireProductionReadyUrl(value, "STRIPE_API_BASE_URL");
        String host = normalizeHost(uri.getHost());
        if (!STRIPE_API_HOST.equals(host)) {
            throw new IllegalStateException(
                    "STRIPE_API_BASE_URL must be https://api.stripe.com when the prod profile is active.");
        }
    }

    private void requireLiveStripeApiKey(String value) {
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

    private void requireStripeWebhookSecret(String value) {
        if (!value.trim().startsWith("whsec_")) {
            throw new IllegalStateException(
                    "STRIPE_WEBHOOK_SECRET must be a Stripe webhook signing secret when the prod profile is active.");
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
