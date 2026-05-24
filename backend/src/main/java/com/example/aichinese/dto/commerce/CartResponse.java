package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;
import java.util.List;

/**
 * Live student cart returned by
 * {@code GET /api/v1/students/cart} and as the response side of
 * every cart-mutating endpoint
 * ({@code POST /cart/items}, {@code DELETE /cart/items/{id}},
 * {@code DELETE /cart/clear}). Returning the freshly-saved cart
 * from every mutating call lets the front-end re-render without a
 * follow-up GET.</p>
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
 * existing currency name in the message. {@link #currencyCode}
 * stays {@code null} on a fresh empty cart and gets set when the
 * first item lands.</p>
 *
 * <p>Carts are persisted ({@link com.example.aichinese.entity.Cart})
 * so a student can add items on one device and check out on
 * another. The cart is auto-cleared on successful checkout — the
 * service replaces it with a fresh empty cart.</p>
 */
public record CartResponse(
        Long cartId,
        List<CartItemResponse> items,
        BigDecimal totalAmount,
        String currencyCode,
        boolean empty) {
}
