package com.example.aichinese.dto.commerce;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Canonicalisation</h2>
 * <p>The compact constructor strips leading/trailing whitespace from
 * {@link #itemType} and {@link #featureKey}, and normalises a blank
 * {@link #featureKey} to {@code null} — symmetric with
 * {@link CartItemRequest}.</p>
 *
 * @param itemType   case-insensitive purchase-item-type
 *                   discriminator; required, 1–{@value
 *                   CommerceDtoConstants#ITEM_TYPE_MAX_LENGTH}
 *                   chars after stripping
 * @param itemId     primary key of the catalogue row selected by
 *                   {@link #itemType}; required and must be
 *                   positive
 * @param featureKey optional subscription-feature tag (max
 *                   {@value CommerceDtoConstants#FEATURE_KEY_MAX_LENGTH}
 *                   chars after stripping); {@code null} for
 *                   content purchases
 */
@JsonPropertyOrder({ "itemType", "itemId", "featureKey" })
public record CheckoutSessionItemRequest(
        @NotBlank(message = "Item type is required.")
        @Size(max = CommerceDtoConstants.ITEM_TYPE_MAX_LENGTH,
                message = "Item type must be {max} characters or fewer.")
        String itemType,
        @NotNull(message = "Item id is required.")
        @Positive(message = "Item id must be positive.")
        Long itemId,
        @Size(max = CommerceDtoConstants.FEATURE_KEY_MAX_LENGTH,
                message = "Feature key must be {max} characters or fewer.")
        String featureKey) {

    public CheckoutSessionItemRequest {
        if (itemType != null) {
            itemType = itemType.strip();
        }
        if (featureKey != null) {
            featureKey = featureKey.strip();
            if (featureKey.isEmpty()) {
                featureKey = null;
            }
        }
    }
}
