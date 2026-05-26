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

    /**
     * Returns every line of {@code cartId} in insertion order so the
     * rendered cart preserves the chronological "newest at the bottom"
     * UX. Backed by the {@code cart_id} foreign-key index.
     */
    List<CartItem> findByCartIdOrderByCreatedAtAsc(Long cartId);

    /**
     * Cheap capacity check used before accepting another cart line. Counting
     * in the database avoids materialising an entire cart just to enforce the
     * service-level checkout-size cap.
     */
    long countByCartId(Long cartId);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code CartItem \u2192 Cart \u2192 StudentProfile \u2192 User} so the
     * remove-item endpoint cannot be tricked into deleting another
     * learner's cart line by guessing its id. The caller maps an empty
     * {@link Optional} to a 404 to avoid disclosing the existence of
     * cross-user cart-item ids.
     */
    Optional<CartItem> findByIdAndCartStudentUserId(Long id, Long userId);

    /**
     * Pre-insert duplicate check for the add-to-cart flow. Backed by
     * the {@code (cart_id, item_type, item_id)} <em>unique</em> index
     * which is the platform's source of truth for "one line per artefact
     * per cart"; the in-flight check here gives the user-facing 400
     * before the constraint violation surfaces, while the constraint
     * itself remains the race-safe enforcement boundary (a concurrent
     * add for the same artefact will still surface as a
     * {@code DataIntegrityViolationException} that the cart service
     * translates to the same 400).
     */
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
