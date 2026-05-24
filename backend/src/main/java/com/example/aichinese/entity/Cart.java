package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Shopping cart owned by a single {@link StudentProfile}.
 *
 * <p>Modelled as a {@link jakarta.persistence.OneToOne} with a unique
 * constraint on {@code student_id}: every student has at most one cart
 * row at any time. {@code CartService.getOrCreateCart} lazily
 * provisions the row on first add-to-cart.</p>
 *
 * <p>{@link #active} is reserved for future "saved-for-later" baskets;
 * the current checkout flow simply deletes a cart's
 * {@link CartItem CartItems} after successful payment rather than
 * toggling this flag.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "carts", indexes = {
        @Index(name = "idx_carts_student_id", columnList = "student_id", unique = true)
})
public class Cart extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, unique = true)
    private StudentProfile student;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
