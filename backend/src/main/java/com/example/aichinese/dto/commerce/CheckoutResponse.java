package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.util.List;

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
 */
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
}
