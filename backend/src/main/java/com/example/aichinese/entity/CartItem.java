package com.example.aichinese.entity;

import java.math.BigDecimal;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Single line in a {@link Cart}, polymorphically referencing a
 * purchasable artefact via {@link #itemType} ({@link com.example.aichinese.entity.enums.PurchasableItemType})
 * and {@link #itemId}.
 *
 * <p>The pair {@code (cart_id, item_type, item_id)} is uniquely
 * constrained so adding the same {@code Lesson} or {@code Package}
 * twice is a no-op rather than producing duplicate rows; the cart
 * service quietly increments {@link #quantity} instead.</p>
 *
 * <p>{@link #unitPrice} is captured at add-to-cart time, not resolved
 * live from the underlying artefact, so a price change between adding
 * to cart and checkout never silently overcharges the buyer; any drift
 * is reconciled explicitly in {@code CheckoutService}.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "cart_items", indexes = {
        @Index(name = "idx_cart_items_cart_id", columnList = "cart_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_cart_items_cart_item", columnNames = { "cart_id", "item_type", "item_id" })
})
public class CartItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @Column(name = "item_type", nullable = false, length = 50)
    private String itemType;

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 1;

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
}
