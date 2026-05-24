package com.example.aichinese.provider;

import com.example.aichinese.dto.commerce.PaymentProviderCheckoutResult;
import com.example.aichinese.dto.commerce.PaymentProviderStatusResult;
import com.example.aichinese.service.model.CommerceCheckout;

/**
 * Provider-agnostic abstraction for the payment-gateway integration.
 *
 * <p>Three implementations are wired in the Spring context:</p>
 * <ul>
 *   <li>{@link NoOpPaymentProviderClient} ({@code provider=no-op}) —
 *       always returns "unavailable", forcing every checkout into the
 *       service-layer "payments not configured" branch. The default for
 *       fresh deployments where Stripe credentials have not yet been
 *       provisioned.</li>
 *   <li>{@link MockPaymentProviderClient} ({@code provider=mock}) —
 *       synthesises a deterministic success response without contacting
 *       any external service. Used for development and integration
 *       tests; refused by {@link com.example.aichinese.config.PaymentProductionGuard}
 *       when the {@code prod} profile is active.</li>
 *   <li>{@link StripePaymentProviderClient} ({@code provider=stripe}) —
 *       the real production adapter that talks to Stripe Checkout.</li>
 * </ul>
 *
 * <p>The {@code PaymentService} selects the right implementation by
 * matching {@link #providerName()} against the configured
 * {@code app.payment.provider} value, so adding a fourth provider is a
 * pure-addition: implement this interface, register the bean, and add
 * the new short name to
 * {@link com.example.aichinese.config.PaymentProperties} /
 * {@link com.example.aichinese.config.PaymentProductionGuard} as
 * appropriate.</p>
 *
 * <h2>Failure model</h2>
 * <p>Implementations must never throw network or parsing exceptions
 * out of these methods — every failure path returns a result with
 * {@code success=false} carrying a user-safe message (the canonical
 * {@link #PAYMENT_UNAVAILABLE_MESSAGE} unless the provider has a more
 * specific safe phrasing). The service layer maps that into a
 * {@code 503}-shaped {@code ApiResponse} for the caller. This keeps
 * payment outages from leaking provider internals to the client.</p>
 */
public interface PaymentProviderClient {

    /**
     * Canonical user-facing message used by every implementation when
     * the provider is unavailable / mis-configured / unreachable.
     * Defined here so all three implementations agree on the wording
     * and the front-end's exact-string match continues to work
     * regardless of which provider is active.
     */
    String PAYMENT_UNAVAILABLE_MESSAGE = "Payment service is currently unavailable. Please try again later.";

    /**
     * Short identifier ({@code "stripe"} / {@code "mock"} / {@code "no-op"})
     * matched against the configured {@code app.payment.provider}
     * setting to select the active implementation.
     */
    String providerName();

    /**
     * Creates a hosted-checkout session at the provider and returns the
     * URL the client should redirect to. Must never throw — wrap any
     * provider-side exception into an unavailable result.
     */
    PaymentProviderCheckoutResult createCheckoutSession(CommerceCheckout checkout);

    /**
     * Polls the provider for the latest status of a previously-created
     * session. Used both by the success-page poll and by the periodic
     * reconciliation sweep that catches webhooks the platform missed
     * during a downtime window. Must never throw.
     */
    PaymentProviderStatusResult getPaymentStatus(String providerReference);
}
