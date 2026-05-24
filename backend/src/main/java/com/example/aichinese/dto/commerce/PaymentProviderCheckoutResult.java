package com.example.aichinese.dto.commerce;

/**
 * Internal payload returned by
 * {@code PaymentProviderClient.createCheckoutSession} (currently
 * implemented by {@code StripePaymentProviderClient}) and consumed
 * by {@code PaymentService} when materialising
 * {@link CheckoutResponse}.
 *
 * <p>NOT directly serialised to a controller endpoint — this DTO
 * is the boundary contract between the application service and the
 * payment-provider adapter. Keeping it as a record makes
 * provider-level errors uniform across providers (Stripe, future
 * Razorpay, etc.) without leaking provider-specific exceptions
 * into the application layer.</p>
 *
 * <h2>Failure mode</h2>
 * <p>The {@link #unavailable(String, String)} static factory is the
 * canonical "soft failure" constructor — when the provider is
 * unreachable / mis-configured / circuit-broken,
 * {@code PaymentService} catches the failure and returns a
 * {@link CheckoutResponse} with
 * {@link CheckoutResponse#paymentAvailable()} {@code = false} and
 * the {@link #message} forwarded. Hard failures (e.g. invalid
 * webhook signature) bypass this DTO and raise an exception
 * directly.</p>
 *
 * <p>{@link #sessionReference} is the provider's own session id
 * (e.g. Stripe {@code cs_test_...}) persisted on
 * {@link com.example.aichinese.entity.PaymentRecord} so the webhook
 * handler can correlate the asynchronous status callbacks.</p>
 *
 * @param available         {@code true} when the provider returned
 *                          a usable checkout URL
 * @param providerName      e.g. {@code "stripe"}; lower-case provider
 *                          identifier echoed verbatim from
 *                          {@code PaymentProviderClient.providerName()}
 * @param sessionReference  provider session id; {@code null} when
 *                          {@link #available} is {@code false}
 * @param checkoutUrl       hosted-checkout URL the front-end
 *                          redirects to; {@code null} when
 *                          {@link #available} is {@code false}
 * @param providerStatus    short status string echoed from the
 *                          provider response, or
 *                          {@link #UNAVAILABLE_PROVIDER_STATUS} for
 *                          soft failures
 * @param message           human-readable status / error blurb
 */
public record PaymentProviderCheckoutResult(
        boolean available,
        String providerName,
        String sessionReference,
        String checkoutUrl,
        String providerStatus,
        String message) {

    /**
     * Canonical {@link #providerStatus} value reported by the
     * {@link #unavailable(String, String)} soft-failure factory and
     * mirrored in {@link PaymentProviderStatusResult#UNAVAILABLE_PROVIDER_STATUS}
     * so {@code PaymentService} can branch on a single shared
     * sentinel rather than a duplicated string literal.
     */
    public static final String UNAVAILABLE_PROVIDER_STATUS = "UNAVAILABLE";

    /**
     * Canonical "the provider is currently unreachable / misconfigured"
     * factory. Produces a non-fatal result that
     * {@code PaymentService} can pass through to the front-end so
     * the student sees an actionable message rather than a 500.
     *
     * @param providerName provider name to echo on the result
     * @param message      human-readable explanation
     * @return a soft-failure result with
     *         {@link #available()} {@code == false} and
     *         {@link #providerStatus()} {@code ==}
     *         {@link #UNAVAILABLE_PROVIDER_STATUS}
     */
    public static PaymentProviderCheckoutResult unavailable(String providerName, String message) {
        return new PaymentProviderCheckoutResult(
                false, providerName, null, null, UNAVAILABLE_PROVIDER_STATUS, message);
    }
}
