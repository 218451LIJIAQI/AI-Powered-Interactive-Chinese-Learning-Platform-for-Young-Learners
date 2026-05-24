package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * One purchasable item in a direct-checkout request. Used both:
 * <ul>
 *   <li>Standalone as the body of
 *       {@code POST /api/v1/students/checkout/buy-now} (single-item
 *       fast purchase that bypasses the cart entirely).</li>
 *   <li>Embedded inside {@link CheckoutSessionRequest#items()} for
 *       multi-item checkout flows.</li>
 * </ul>
 *
 * <p>Field-for-field identical to {@link CartItemRequest}; the
 * separate type exists so the cart API and the checkout API can
 * evolve independently without forcing one to absorb the other's
 * future fields. See {@link CartItemRequest} for the discussion of
 * {@link #itemType} vocabulary and {@link #featureKey} usage.</p>
 *
 * <p>The buy-now path runs the same eligibility / price-snapshot
 * logic as a cart checkout — the student gets exactly one chance
 * to pay at the displayed price; abandoning the Stripe checkout
 * does not retain anything since this DTO is request-only.</p>
 */
public record CheckoutSessionItemRequest(
        @NotBlank(message = "Item type is required.")
        @Size(max = 30, message = "Item type must be 30 characters or fewer.")
        String itemType,
        @NotNull(message = "Item id is required.")
        @Positive(message = "Item id must be positive.")
        Long itemId,
        @Size(max = 100, message = "Feature key must be 100 characters or fewer.")
        String featureKey) {
}
