package com.example.aichinese.dto.commerce;

import com.example.aichinese.entity.enums.PaymentStatus;

/**
 * Internal payload returned by
 * {@code PaymentProviderClient.fetchStatus}, consumed by
 * {@code PaymentService.verifyPaymentStatus} when the front-end
 * polls {@code GET /api/v1/payments/status/{paymentId}} after the
 * student returns from the hosted checkout.
 *
 * <p>The poll path is a defensive complement to the asynchronous
 * Stripe webhook (see {@link StripeWebhookResultResponse}). Two
 * independent feeds keep payment state correct even when one
 * channel is delayed:</p>
 * <ul>
 *   <li>The webhook is the canonical source of truth and updates
 *       payment status as soon as Stripe fires it.</li>
 *   <li>The poll is the fallback in case the webhook is delayed by
 *       the network — the front-end calls it on the
 *       payment-success page so the student sees the final state
 *       even before the webhook lands.</li>
 * </ul>
 *
 * <p>Mirrors {@link PaymentProviderCheckoutResult} as the
 * "fetch-status equivalent" — same {@link #available} /
 * {@link #unavailable(String, String)} soft-failure pattern. The
 * key difference is that this DTO carries a
 * {@link PaymentStatus} (the canonical mapped status, not just the
 * provider-specific string) so the consumer can act on it
 * directly.</p>
 *
 * <h2>{@link #unavailable(String, String)}</h2>
 * <p>The soft-failure factory uses {@link PaymentStatus#FAILED} as
 * the safe default — when the provider is unreachable, the
 * platform must not optimistically mark the payment as
 * {@code PAID}. The student can retry later; the webhook will
 * still resolve the true state.</p>
 *
 * @param available        {@code true} when the provider returned a
 *                         usable status fetch
 * @param providerName     lower-case provider identifier echoed
 *                         from {@code PaymentProviderClient.providerName()}
 * @param sessionReference provider session id correlated against
 *                         {@link com.example.aichinese.entity.PaymentRecord#getProviderReference()};
 *                         {@code null} when {@link #available} is
 *                         {@code false}
 * @param paymentStatus    canonical platform-side mapped status
 *                         that the consumer branches on
 * @param providerStatus   raw status string echoed for diagnostics,
 *                         or {@link #UNAVAILABLE_PROVIDER_STATUS}
 *                         for soft failures
 * @param message          human-readable status / error blurb
 */
public record PaymentProviderStatusResult(
        boolean available,
        String providerName,
        String sessionReference,
        PaymentStatus paymentStatus,
        String providerStatus,
        String message) {

    /**
     * Canonical {@link #providerStatus} value reported by the
     * {@link #unavailable(String, String)} soft-failure factory and
     * mirrored in
     * {@link PaymentProviderCheckoutResult#UNAVAILABLE_PROVIDER_STATUS}.
     * Aliased here so callers depending on a {@link PaymentProviderStatusResult}
     * do not need to import the sibling DTO just to reach the
     * sentinel.
     */
    public static final String UNAVAILABLE_PROVIDER_STATUS =
            PaymentProviderCheckoutResult.UNAVAILABLE_PROVIDER_STATUS;

    /**
     * Soft-failure factory for transient provider unavailability.
     *
     * @param providerName provider name to echo on the result
     * @param message      human-readable explanation
     * @return a soft-failure result with
     *         {@link #available()} {@code == false},
     *         {@link #paymentStatus()} {@code == PaymentStatus.FAILED}
     *         (the safe default — never optimistically mark a
     *         payment paid when the provider is unreachable), and
     *         {@link #providerStatus()} {@code ==}
     *         {@link #UNAVAILABLE_PROVIDER_STATUS}
     */
    public static PaymentProviderStatusResult unavailable(String providerName, String message) {
        return new PaymentProviderStatusResult(
                false, providerName, null, PaymentStatus.FAILED, UNAVAILABLE_PROVIDER_STATUS, message);
    }
}
