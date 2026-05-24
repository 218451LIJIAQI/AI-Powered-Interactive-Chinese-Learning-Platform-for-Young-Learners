package com.example.aichinese.dto.commerce;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/students/cart/items} — the student
 * adds one purchasable artefact to their cart.
 *
 * <h2>{@link #itemType} / {@link #itemId}</h2>
 * <p>{@link #itemType} is the case-insensitive discriminator for
 * which catalogue table {@link #itemId} refers to. The supported
 * vocabulary is parsed server-side via
 * {@link com.example.aichinese.entity.enums.PurchasableItemType#fromValue(String)}
 * — currently {@code "LESSON"}, {@code "PACKAGE"}, {@code "MODULE"}
 * (legacy {@code "COURSE"} also accepted), plus subscription-style
 * values ({@code "PREMIUM_AI"} for time-bounded feature access
 * keyed by {@link #featureKey}). An unknown {@link #itemType}
 * raises a {@code 400}.</p>
 *
 * <p>The legacy {@code MODULE} item type is retained but
 * de-prioritised: only individual lessons, curated packages, and
 * AI subscriptions are normally sellable today; a student wanting
 * a whole module's content typically buys the lesson list or the
 * containing package.</p>
 *
 * <h2>{@link #featureKey}</h2>
 * <p>Optional tag relevant only when {@link #itemType} addresses a
 * feature/subscription product (e.g. {@code "AI_CHAT_PRO"}); ignored
 * for content item types. Capped at
 * {@value CommerceDtoConstants#FEATURE_KEY_MAX_LENGTH} characters.</p>
 *
 * <h2>Deduplication / quantity</h2>
 * <p>The service deduplicates: adding the same {@code (itemType,
 * itemId)} pair twice raises a {@code 400} ("already in your
 * cart"). Quantities are always 1 — content is non-stackable.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Canonicalisation</h2>
 * <p>The compact constructor strips leading/trailing whitespace from
 * {@link #itemType} and {@link #featureKey}, and normalises a blank
 * {@link #featureKey} to {@code null}. This keeps the in-memory DTO
 * stable regardless of input padding so downstream services do not
 * have to re-trim.</p>
 *
 * @param itemType    case-insensitive purchase-item-type
 *                    discriminator; required, 1–{@value
 *                    CommerceDtoConstants#ITEM_TYPE_MAX_LENGTH}
 *                    chars after stripping
 * @param itemId      primary key of the catalogue row selected by
 *                    {@link #itemType}; required and must be
 *                    positive
 * @param featureKey  optional subscription-feature tag (max
 *                    {@value CommerceDtoConstants#FEATURE_KEY_MAX_LENGTH}
 *                    chars after stripping); {@code null} for
 *                    content purchases
 */
@JsonPropertyOrder({ "itemType", "itemId", "featureKey" })
public record CartItemRequest(
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

    public CartItemRequest {
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
