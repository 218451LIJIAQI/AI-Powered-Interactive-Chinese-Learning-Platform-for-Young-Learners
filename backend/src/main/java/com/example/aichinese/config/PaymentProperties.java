package com.example.aichinese.config;

import java.util.Locale;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed binding for the {@code app.payment.*} configuration block.
 *
 * <p>Captures the success / cancel redirect URLs, the provider
 * selector, and the Stripe-specific credentials. The compact
 * constructor performs eager validation:</p>
 * <ul>
 *   <li>Success / cancel / Stripe-API URLs are validated as absolute
 *       HTTP(S) URLs (with sane local-development defaults).</li>
 *   <li>Provider is normalised to one of {@code mock} / {@code no-op} /
 *       {@code stripe}; an unrecognised value fails fast at startup.</li>
 *   <li>Stripe credentials are trimmed; non-empty / live-key checks
 *       happen later in {@link PaymentProductionGuard} so the
 *       defaults stay valid for dev profiles.</li>
 * </ul>
 *
 * <p>Production gates ({@code sk_live_} prefix on the API key,
 * exact {@code api.stripe.com} hostname, etc.) live in
 * {@link PaymentProductionGuard} rather than here so dev profiles can
 * still bind {@code sk_test_...} values without failing the property
 * record's compact constructor.</p>
 */
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
    private static final int MAX_URL_LENGTH = 2048;
    private static final String DEFAULT_SUCCESS_URL = "http://localhost:5173/payment/success";
    private static final String DEFAULT_CANCEL_URL = "http://localhost:5173/payment/cancel";
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
        // strip() over trim() so a CJK fullwidth space pasted from an editor
        // does not silently mask a configured URL.
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
            throw new IllegalArgumentException("Payment provider must be one of: mock, no-op, stripe.");
        }
        return normalized;
    }

    /**
     * Diagnostic representation that masks both Stripe secrets. The
     * auto-generated record toString would emit them verbatim, so a stray
     * {@code log.info(paymentProperties)} shipping to production would
     * leak the live API key and webhook signing secret. We report only
     * the presence flag and \u2014 for the API key only \u2014 the
     * {@code sk_live_} / {@code sk_test_} prefix because the live/test
     * distinction is operationally useful at a glance.
     */
    @Override
    public String toString() {
        return "PaymentProperties[successUrl=" + successUrl
                + ", cancelUrl=" + cancelUrl
                + ", mockSuccessEnabled=" + mockSuccessEnabled
                + ", provider=" + provider
                + ", stripeApiBaseUrl=" + stripeApiBaseUrl
                + ", stripeApiKey=" + maskedStripeApiKey()
                + ", stripeWebhookSecret=" + (stripeWebhookSecret == null || stripeWebhookSecret.isBlank() ? "[unset]" : "[set]")
                + "]";
    }

    private String maskedStripeApiKey() {
        if (stripeApiKey == null || stripeApiKey.isBlank()) {
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
