package com.example.aichinese.dto.commerce;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PaymentStatus;

/**
 * Result acknowledgement returned by
 * {@code POST /api/v1/payments/webhooks/stripe} — the public
 * webhook endpoint Stripe POSTs against to deliver asynchronous
 * payment events.
 *
 * <p>The endpoint validates the {@code Stripe-Signature} header
 * via the configured webhook secret and rejects unsigned /
 * mis-signed bodies with {@code 401}. Only a strict subset of
 * Stripe events is processed (currently
 * {@code checkout.session.completed} and friends — see
 * {@code StripeWebhookService.handleWebhook}); other events are
 * acknowledged with {@link #processed} {@code = false} but still
 * return {@code 200 OK} so Stripe does not retry.</p>
 *
 * <h2>Idempotency</h2>
 * <p>{@link #eventId} is the Stripe event id (e.g.
 * {@code "evt_..."}) — every event is deduplicated against an
 * idempotency table on the way in, so replaying the same webhook
 * (Stripe retries on transient 5xx) updates payment state at most
 * once. Replays return {@link #processed} {@code = true} echoing
 * the original outcome, never an error.</p>
 *
 * <h2>{@link #paymentStatus}</h2>
 * <p>The canonical platform-side {@link PaymentStatus} after the
 * webhook update lands. {@code null} when the event did not
 * resolve to a known payment row (e.g. event for a payment we
 * never created).</p>
 *
 * @param eventId         Stripe event id (idempotency key)
 * @param eventType       Stripe event type (e.g.
 *                        {@code "checkout.session.completed"})
 * @param paymentId       resolved
 *                        {@link com.example.aichinese.entity.PaymentRecord}
 *                        id; {@code null} when the event did not
 *                        match a payment row
 * @param paymentStatus   canonical platform status after the update
 * @param providerStatus  raw status string forwarded for diagnostics
 * @param processed       {@code true} when the webhook actually
 *                        mutated state (or replayed a previously
 *                        processed event); {@code false} for
 *                        events of unhandled types
 */
@JsonPropertyOrder({
        "eventId",
        "eventType",
        "paymentId",
        "paymentStatus",
        "providerStatus",
        "processed"
})
public record StripeWebhookResultResponse(
        String eventId,
        String eventType,
        Long paymentId,
        PaymentStatus paymentStatus,
        String providerStatus,
        boolean processed) {
}
