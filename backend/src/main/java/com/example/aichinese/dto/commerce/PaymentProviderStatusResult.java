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
 */
public record PaymentProviderStatusResult(
        boolean available,
        String providerName,
        String sessionReference,
        PaymentStatus paymentStatus,
        String providerStatus,
        String message) {

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
     *         {@link #providerStatus()} {@code == "UNAVAILABLE"}
     */
    public static PaymentProviderStatusResult unavailable(String providerName, String message) {
        return new PaymentProviderStatusResult(false, providerName, null, PaymentStatus.FAILED, "UNAVAILABLE", message);
    }
}
