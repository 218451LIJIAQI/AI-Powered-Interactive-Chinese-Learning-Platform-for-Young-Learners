package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

/**
 * One line item in a cart, surfaced inside:
 * <ul>
 *   <li>{@link CartResponse#items()} — the live cart.</li>
 *   <li>{@link CheckoutResponse#items()} — snapshot at checkout
 *       time.</li>
 *   <li>{@link StudentPaymentRecordResponse#items()} /
 *       {@link AdminPaymentRecordResponse#items()} — historical
 *       order receipt.</li>
 * </ul>
 *
 * <p>Same record shape is reused for both the editable cart and
 * the immutable order/payment receipt; the only field whose
 * meaning shifts is {@link #cartItemId} (the actual cart row id
 * for live carts, or the historical {@code order_items.id} for
 * receipts — clients can ignore it once the cart is checked
 * out).</p>
 *
 * <h2>{@link #title}</h2>
 * <p>Frozen at insert/checkout time — captured from the underlying
 * lesson/package/feature title at the moment the student added the
 * item. This is the same defensive snapshotting documented on
 * {@code PackageItemResponse#titleSnapshot()}: the receipt must
 * always show what the student bought, even if the teacher later
 * renames the underlying content.</p>
 *
 * <h2>{@link #unitPrice} / {@link #subtotal} / {@link #currencyCode}</h2>
 * <p>{@link #unitPrice} is also frozen at insert time so price
 * changes after the item lands in the cart don't surprise the
 * student at checkout. {@link #subtotal} is
 * {@code unitPrice * quantity} (always 1, since content is
 * non-stackable). {@link #currencyCode} is the ISO 4217 code (e.g.
 * {@code "MYR"}, {@code "USD"}); the cart enforces a single
 * currency per cart and refuses cross-currency mixes.</p>
 *
 * <p>{@link #teacherOwned} is {@code true} when the calling
 * teacher (if applicable) is the author of the underlying content
 * — used to grey out "your own content" rows on a teacher-account
 * cart preview to prevent self-purchase confusion. Primitive
 * {@code boolean} so the JSON wire format always emits a value.</p>
 */
public record CartItemResponse(
        Long cartItemId,
        String itemType,
        Long itemId,
        String title,
        BigDecimal unitPrice,
        String currencyCode,
        Integer quantity,
        BigDecimal subtotal,
        boolean teacherOwned) {
}
