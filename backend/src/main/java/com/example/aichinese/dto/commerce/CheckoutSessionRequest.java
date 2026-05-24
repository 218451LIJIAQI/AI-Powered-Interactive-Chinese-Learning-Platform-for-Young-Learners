package com.example.aichinese.dto.commerce;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Internal multi-item checkout request shape used by
 * {@code CheckoutService} when materialising a checkout from either
 * the live cart or a direct buy-now payload. Not directly bound to
 * a single controller endpoint; the public flow is split into:
 * <ul>
 *   <li>{@code POST /api/v1/students/checkout/cart} — server reads
 *       the cart and synthesizes a request with
 *       {@link CheckoutSource#CART}.</li>
 *   <li>{@code POST /api/v1/students/checkout/buy-now} — accepts
 *       a single {@link CheckoutSessionItemRequest} and synthesizes
 *       a request with {@link CheckoutSource#BUY_NOW}.</li>
 * </ul>
 *
 * <p>The {@link #source} discriminator lets the service log the
 * provenance of each order ("from cart" vs "buy now") without
 * recomputing it from inferred signals — useful for the future
 * conversion-funnel analytics. The
 * {@link jakarta.validation.Valid} cascade on {@link #items} runs
 * Bean Validation against each child {@link CheckoutSessionItemRequest}
 * so the whole request is rejected atomically when any line item
 * fails.</p>
 *
 * <h2>{@link CheckoutSource}</h2>
 * <ul>
 *   <li>{@link CheckoutSource#CART} — populated from the persisted
 *       student cart (see {@link CartResponse}).</li>
 *   <li>{@link CheckoutSource#BUY_NOW} — direct checkout that
 *       bypasses the cart entirely.</li>
 * </ul>
 */
public record CheckoutSessionRequest(
        @NotEmpty(message = "At least one checkout item is required.")
        List<@Valid CheckoutSessionItemRequest> items,
        @NotNull(message = "Checkout source is required.")
        CheckoutSource source) {

    /**
     * Discriminates the provenance of a checkout for analytics and
     * audit. Server-supplied (the public endpoints don't accept it
     * from clients) — the cart-checkout endpoint always sets
     * {@link #CART} and the buy-now endpoint always sets
     * {@link #BUY_NOW}.
     */
    public enum CheckoutSource {
        /** Checkout was synthesised from the persisted student cart. */
        CART,
        /** Checkout was a one-shot direct purchase bypassing the cart. */
        BUY_NOW
    }
}
