package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * non-stackable). {@link #currencyCode} is the ISO 4217 three-letter
 * uppercase code (e.g. {@code "MYR"}, {@code "USD"}; matches
 * {@link CommerceDtoConstants#CURRENCY_CODE_PATTERN}); the cart
 * enforces a single currency per cart and refuses cross-currency
 * mixes.</p>
 *
 * <p>{@link #teacherOwned} is {@code true} when the calling
 * teacher (if applicable) is the author of the underlying content
 * — used to grey out "your own content" rows on a teacher-account
 * cart preview to prevent self-purchase confusion. Primitive
 * {@code boolean} so the JSON wire format always emits a value.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param cartItemId   primary key of the cart-row (live cart) or
 *                     order-line-item (receipt); {@code null} on
 *                     synthesised free-checkout receipts where no
 *                     persistent cart row was ever created
 * @param itemType     uppercase
 *                     {@link com.example.aichinese.entity.enums.PurchasableItemType}
 *                     name (e.g. {@code "LESSON"}, {@code "PACKAGE"})
 * @param itemId       primary key of the underlying catalogue row
 *                     selected by {@link #itemType}
 * @param title        snapshot title at insert/checkout time;
 *                     stable across later content renames
 * @param unitPrice    snapshot price at insert/checkout time,
 *                     normalised to
 *                     {@link CommerceDtoConstants#MONEY_FRACTION_DIGITS}
 *                     decimal places
 * @param currencyCode ISO 4217 three-letter uppercase currency
 *                     (matches
 *                     {@link CommerceDtoConstants#CURRENCY_CODE_PATTERN})
 * @param quantity     always {@code 1} today (content is
 *                     non-stackable); boxed to align with the
 *                     {@code Integer} entity column
 * @param subtotal     {@code unitPrice * quantity}, normalised
 * @param teacherOwned {@code true} when the calling user is the
 *                     authoring teacher of the underlying content
 *                     and should be discouraged from self-purchase
 */
@JsonPropertyOrder({
        "cartItemId",
        "itemType",
        "itemId",
        "title",
        "unitPrice",
        "currencyCode",
        "quantity",
        "subtotal",
        "teacherOwned"
})
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
