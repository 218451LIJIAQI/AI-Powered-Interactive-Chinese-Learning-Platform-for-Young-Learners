package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.CartItem;

/**
 * Persistence operations for shopping-cart lines (one {@link CartItem} per
 * artefact added to a {@link com.example.aichinese.entity.Cart}).
 *
 * <p>The {@code (cart_id, item_type, item_id)} unique constraint on the
 * underlying table is leveraged by
 * {@link #findByCartIdAndItemTypeAndItemId(Long, String, Long)} so the
 * cart service can detect an existing line and bump its quantity rather
 * than insert a duplicate row.</p>
 */
public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByCreatedAtAsc(Long cartId);

    Optional<CartItem> findByIdAndCartStudentUserId(Long id, Long userId);

    Optional<CartItem> findByCartIdAndItemTypeAndItemId(Long cartId, String itemType, Long itemId);

    /**
     * Bulk-removes every line attached to the given cart in a single
     * {@code DELETE} statement.
     *
     * <p>Annotated with {@link Modifying} + {@link Transactional} so the
     * query runs as a single SQL DELETE rather than Spring Data's default
     * derived-{@code deleteBy} behaviour, which would fetch every matching
     * row first and call {@code EntityManager#remove(entity)} per line.
     * Since {@link CartItem} declares no {@code @PreRemove} lifecycle
     * callbacks and no cascading children, the bulk DELETE is semantically
     * identical and substantially cheaper — important on the
     * {@code checkout} hot path, which clears the cart on every
     * successful order.</p>
     */
    @Modifying
    @Transactional
    @Query("delete from CartItem ci where ci.cart.id = :cartId")
    int deleteByCartId(@Param("cartId") Long cartId);
}
