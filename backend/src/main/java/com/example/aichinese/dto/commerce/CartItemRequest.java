package com.example.aichinese.dto.commerce;

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
 * vocabulary mirrors the public-facing
 * {@link com.example.aichinese.entity.enums.ManagedContentType}
 * subset that participates in commerce ({@code "LESSON"},
 * {@code "PACKAGE"}) plus subscription-style values
 * ({@code "FEATURE"} for time-bounded feature access keyed by
 * {@link #featureKey}). The exact set is enforced server-side in
 * {@code CartService.addItem}; an unknown {@link #itemType} returns
 * a {@code 400}.</p>
 *
 * <p>Modules are intentionally <em>not</em> a purchasable item type
 * — only individual lessons and curated packages are sellable; a
 * student wanting a whole module must buy each lesson or buy the
 * package that contains the module.</p>
 *
 * <h2>{@link #featureKey}</h2>
 * <p>Optional tag relevant only when {@link #itemType} addresses a
 * feature/subscription product (e.g. {@code "AI_CHAT_PRO"} for
 * unlocked AI tutor sessions). Ignored for {@code LESSON} or
 * {@code PACKAGE} items. Capped at 100 chars.</p>
 *
 * <p>The service deduplicates: adding the same item twice does
 * <em>not</em> create two cart rows, it merely updates the existing
 * row's {@code updated_at} so the front-end can show a "added 1
 * minute ago" hint. Quantities are always 1 — content is
 * non-stackable in this storefront.</p>
 */
public record CartItemRequest(
        @NotBlank(message = "Item type is required.")
        @Size(max = 30, message = "Item type must be 30 characters or fewer.")
        String itemType,
        @NotNull(message = "Item id is required.")
        @Positive(message = "Item id must be positive.")
        Long itemId,
        @Size(max = 100, message = "Feature key must be 100 characters or fewer.")
        String featureKey) {
}
