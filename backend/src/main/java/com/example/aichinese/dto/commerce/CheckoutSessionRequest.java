package com.example.aichinese.dto.commerce;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Immutability</h2>
 * <p>The compact constructor defensively wraps {@link #items} with
 * an unmodifiable copy. The list is assembled by the service from
 * a freshly-built collection today, but the defensive copy keeps
 * the request shape immutable in case a future refactor passes a
 * mutable list.</p>
 *
 * @param items  per-line checkout items; {@link NotEmpty}-validated
 *               and {@link Valid}-cascaded so each child
 *               {@link CheckoutSessionItemRequest} is also validated;
 *               wrapped in an unmodifiable copy by the compact
 *               constructor; never {@code null} (an empty list trips
 *               {@link NotEmpty} validation)
 * @param source provenance discriminator; required, server-supplied
 */
@JsonPropertyOrder({ "items", "source" })
public record CheckoutSessionRequest(
        @NotEmpty(message = "At least one checkout item is required.")
        List<@NotNull(message = "Checkout item must not be null.") @Valid CheckoutSessionItemRequest> items,
        @NotNull(message = "Checkout source is required.")
        CheckoutSource source) {

    public CheckoutSessionRequest {
        items = immutableListOrEmpty(items);
    }

    /**
     * Discriminates the provenance of a checkout for analytics and
     * audit. Server-supplied (the public endpoints don't accept it
     * from clients) — the cart-checkout endpoint always sets
     * {@link #CART} and the buy-now endpoint always sets
     * {@link #BUY_NOW}.
     *
     * <p>Persisted as a string on
     * {@link com.example.aichinese.entity.Order#setCheckoutSource}
     * via {@link Enum#name()}, so the constants are part of the
     * database contract — they MUST NOT be renamed without a paired
     * Flyway migration.</p>
     */
    public enum CheckoutSource {
        /** Checkout was synthesised from the persisted student cart. */
        CART,
        /** Checkout was a one-shot direct purchase bypassing the cart. */
        BUY_NOW
    }
}
