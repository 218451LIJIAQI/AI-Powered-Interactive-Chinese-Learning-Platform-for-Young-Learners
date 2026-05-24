package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PaymentStatus;

/**
 * Result of either checkout flow:
 * <ul>
 *   <li>{@code POST /api/v1/students/checkout/buy-now} (one-item
 *       direct purchase)</li>
 *   <li>{@code POST /api/v1/students/checkout/cart} (full cart
 *       purchase)</li>
 * </ul>
 *
 * <p>Carries the freshly-created order, payment placeholder, and
 * the URL the front-end must redirect the student to so they can
 * complete the payment with the upstream provider (Stripe today;
 * pluggable via {@code PaymentProvider} interface).</p>
 *
 * <h2>Synchronous flow</h2>
 * <ol>
 *   <li>Service creates an {@link com.example.aichinese.entity.Order}
 *       row and a paired
 *       {@link com.example.aichinese.entity.PaymentRecord} row in
 *       {@link PaymentStatus#PENDING}.</li>
 *   <li>Service calls the provider via
 *       {@code StripePaymentProviderClient.createCheckoutSession}
 *       to obtain a hosted-checkout URL, returned as
 *       {@link #checkoutUrl}.</li>
 *   <li>Front-end redirects the student to {@link #checkoutUrl}.
 *       After success/failure on the provider's UI, the student is
 *       returned to the platform; the platform learns the final
 *       outcome via the asynchronous Stripe webhook (see
 *       {@link StripeWebhookResultResponse}) and updates the
 *       payment row's status accordingly.</li>
 * </ol>
 *
 * <h2>{@link #paymentAvailable}</h2>
 * <p>{@code false} when the configured provider is degraded or
 * unreachable at checkout time (see
 * {@link PaymentProviderCheckoutResult#available()}). In that case
 * {@link #checkoutUrl} is {@code null} and {@link #message} carries
 * a human-readable explanation; the order/payment rows still exist
 * in {@code PENDING} so the student can retry payment later via
 * {@code GET /payments/status/{paymentId}}.</p>
 *
 * <p>{@link #items} snapshots what was purchased; the same items
 * are denormalised into the persisted order line items so the
 * historical receipt
 * ({@link StudentPaymentRecordResponse}/{@link AdminPaymentRecordResponse})
 * remains stable.</p>
 *
 * <h2>Free-checkout fast path</h2>
 * <p>When the cart total is zero (e.g. only free promotional items)
 * the service short-circuits the provider call, immediately marks
 * the payment {@link PaymentStatus#PAID} with {@code "FREE"} as
 * provider/method, sets {@link #checkoutUrl} to {@code null}, and
 * sends back {@link #paymentAvailable} {@code = true} with a
 * "Free item unlocked successfully." message.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Immutability</h2>
 * <p>The compact constructor defensively wraps {@link #items} with
 * an unmodifiable copy: an order receipt is an immutable
 * audit-trail row.</p>
 *
 * @param orderId          primary key of the freshly-created
 *                         {@link com.example.aichinese.entity.Order}
 * @param orderNumber      human-readable order reference (e.g.
 *                         {@code "ORD-XXXXXXXX-XXXXXXXX"})
 * @param paymentId        primary key of the paired
 *                         {@link com.example.aichinese.entity.PaymentRecord}
 *                         in {@link PaymentStatus#PENDING}
 * @param paymentStatus    initial payment status — typically
 *                         {@link PaymentStatus#PENDING}, or
 *                         {@link PaymentStatus#PAID} on the
 *                         free-checkout fast path
 * @param totalAmount      order total in {@link #currencyCode},
 *                         normalised to
 *                         {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                         decimal places
 * @param currencyCode     ISO 4217 three-letter uppercase currency
 *                         (matches
 *                         {@link CommerceDtoConstants#CURRENCY_CODE_PATTERN})
 * @param checkoutUrl      hosted-checkout URL the front-end must
 *                         redirect the student to; {@code null}
 *                         when {@link #paymentAvailable} is
 *                         {@code false} or on the free-checkout
 *                         fast path
 * @param providerName     payment-provider identifier
 *                         (e.g. {@code "stripe"}, {@code "FREE"})
 * @param providerStatus   raw provider status string for
 *                         diagnostics (e.g. {@code "CREATED"})
 * @param paymentAvailable {@code true} when checkout can proceed;
 *                         {@code false} when the provider is
 *                         degraded
 * @param message          human-readable status / error blurb
 *                         shown to the student
 * @param items            immutable line-item snapshot of what was
 *                         purchased; never {@code null}
 */
@JsonPropertyOrder({
        "orderId",
        "orderNumber",
        "paymentId",
        "paymentStatus",
        "totalAmount",
        "currencyCode",
        "checkoutUrl",
        "providerName",
        "providerStatus",
        "paymentAvailable",
        "message",
        "items"
})
public record CheckoutResponse(
        Long orderId,
        String orderNumber,
        Long paymentId,
        PaymentStatus paymentStatus,
        BigDecimal totalAmount,
        String currencyCode,
        String checkoutUrl,
        String providerName,
        String providerStatus,
        boolean paymentAvailable,
        String message,
        List<CartItemResponse> items) {

    public CheckoutResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
