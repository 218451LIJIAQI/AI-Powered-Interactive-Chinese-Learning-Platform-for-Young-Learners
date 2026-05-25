package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 *
 * <p>Uniqueness of {@code student_id} is enforced by a single named
 * constraint ({@code idx_carts_student_id}) declared via
 * {@link UniqueConstraint} on the {@link Table @Table} annotation,
 * matching the {@code alter table carts add constraint
 * idx_carts_student_id unique (student_id)} statement in the Flyway
 * baseline. The {@link #student} association is mapped
 * {@code updatable = false}: a cart is bound to a single student for
 * its entire lifetime, and retargeting is logically impossible because
 * the unique constraint would reject any insert that collided with an
 * existing cart.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "carts",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_carts_student_id", columnNames = "student_id")
        }
)
public class Cart extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false, updatable = false)
    private StudentProfile student;

    @Column(name = "active", nullable = false)
    private boolean active = true;
}
