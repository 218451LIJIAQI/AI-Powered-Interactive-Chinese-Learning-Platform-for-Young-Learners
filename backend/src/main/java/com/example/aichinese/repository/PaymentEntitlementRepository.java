package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PaymentEntitlement;

/**
 * Persistence operations for {@link PaymentEntitlement} \u2014 the
 * row-per-purchase grant that records "{@code studentId} has paid
 * access to {@code (itemType, itemId)}". The
 * {@code StudentLearningAccessService} reads these rows on every
 * gated lesson / module / package / feature access to decide whether
 * to serve the artefact or surface the paywall, so this is a
 * read-hot table.
 *
 * <p>The repository is intentionally small: writes happen only
 * through {@code EntitlementService.grantAccessAfterPayment} (which
 * calls {@link #existsForOrderItem(Long, String, Long, String)} for
 * idempotency before inserting), reads happen through
 * {@link #existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(Long, String, Long)}
 * (the gate check) and {@link #findByOrderId(Long)} (the refund
 * flow).</p>
 */
public interface PaymentEntitlementRepository extends JpaRepository<PaymentEntitlement, Long> {

    /**
     * Hot-path access gate: returns {@code true} when {@code studentId}
     * holds an active entitlement for the given artefact. Used by
     * {@code StudentLearningAccessService} on every lesson / module /
     * package access decision; backed by the composite index
     * {@code (student_id, item_type, item_id, active)} so the lookup
     * stays O(log&nbsp;n) even as the entitlements table grows.
     */
    boolean existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(Long studentId, String itemType, Long itemId);

    /**
     * Returns every entitlement granted by {@code orderId} \u2014 used by
     * the refund flow to deactivate every grant attached to the order
     * being refunded in one round trip.
     */
    List<PaymentEntitlement> findByOrderId(Long orderId);

    /**
     * Idempotency probe used by {@code EntitlementService.grantAccessAfterPayment}
     * to avoid inserting a duplicate entitlement row if {@code markPaymentPaid}
     * runs twice for the same payment (e.g. concurrent calls to
     * {@code verifyPaymentStatus} both seeing PENDING before either has
     * committed).
     *
     * <p>The {@code featureKey} parameter is checked with NULL-equality
     * semantics — JPQL's {@code = :param} would not match NULL on the row
     * side, so we expand the predicate explicitly to cover both the keyed
     * (PREMIUM_AI feature unlock) and unkeyed (lesson/module/package access)
     * variants without false negatives.</p>
     */
    @Query("select case when count(pe) > 0 then true else false end "
            + "from PaymentEntitlement pe "
            + "where pe.order.id = :orderId "
            + "and pe.itemType = :itemType "
            + "and pe.itemId = :itemId "
            + "and ((:featureKey is null and pe.featureKey is null) "
            + "     or pe.featureKey = :featureKey)")
    boolean existsForOrderItem(
            @Param("orderId") Long orderId,
            @Param("itemType") String itemType,
            @Param("itemId") Long itemId,
            @Param("featureKey") String featureKey);
}
