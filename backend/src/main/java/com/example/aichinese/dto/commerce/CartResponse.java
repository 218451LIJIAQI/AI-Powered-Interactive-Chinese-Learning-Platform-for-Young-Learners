package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Live student cart returned by
 * {@code GET /api/v1/students/cart} and as the response side of
 * every cart-mutating endpoint
 * ({@code POST /cart/items}, {@code DELETE /cart/items/{id}},
 * {@code DELETE /cart/clear}). Returning the freshly-saved cart
 * from every mutating call lets the front-end re-render without a
 * follow-up GET.
 *
 * <p>{@link #items} is the full line-item list (each a
 * {@link CartItemResponse}); {@link #totalAmount} is the sum of
 * {@link CartItemResponse#subtotal()} computed server-side so the
 * front-end never recomputes pricing. {@link #empty} is a
 * convenience flag derived from {@code items.isEmpty()} — primitive
 * {@code boolean} so the JSON wire format always emits a value.</p>
 *
 * <h2>{@link #currencyCode}</h2>
 * <p>The cart enforces a single currency. Adding a line item in a
 * different currency raises a {@code 409 Conflict} with the
 * existing currency name in the message. The platform resolves the
 * cart's currency from the system settings
 * ({@code commerce.default-currency}); on a brand-new cart this is
 * the platform default rather than {@code null}.</p>
 *
 * <p>Carts are persisted ({@link com.example.aichinese.entity.Cart})
 * so a student can add items on one device and check out on
 * another. The cart is auto-cleared on successful checkout — the
 * service replaces it with a fresh empty cart.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Immutability</h2>
 * <p>The compact constructor defensively wraps {@link #items} with
 * an unmodifiable copy. Even though the cart is conceptually
 * mutable, callers (controllers, view layers) must not be able to
 * silently mutate the rendered list once it has been handed off.</p>
 *
 * @param cartId       primary key of the underlying
 *                     {@link com.example.aichinese.entity.Cart}
 * @param items        immutable line-item list; never {@code null}
 *                     (an empty list represents an empty cart)
 * @param totalAmount  sum of {@link CartItemResponse#subtotal()}
 *                     across {@link #items}, normalised to
 *                     {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                     decimal places
 * @param currencyCode ISO 4217 three-letter uppercase currency
 *                     (matches
 *                     {@link CommerceDtoConstants#CURRENCY_CODE_PATTERN})
 *                     resolved from the platform settings
 * @param empty        convenience derived from {@code items.isEmpty()}
 *                     so front-end branches don't need to inspect
 *                     the array length
 */
@JsonPropertyOrder({ "cartId", "items", "totalAmount", "currencyCode", "empty" })
public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        BigDecimal totalAmount,
        String currencyCode,
        boolean empty) {

    public CartResponse {
        items = items == null ? List.of() : List.copyOf(items);
    }
}
