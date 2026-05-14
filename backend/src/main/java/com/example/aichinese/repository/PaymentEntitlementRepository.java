package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PaymentEntitlement;

public interface PaymentEntitlementRepository extends JpaRepository<PaymentEntitlement, Long> {

    boolean existsByStudentIdAndItemTypeAndItemIdAndActiveTrue(Long studentId, String itemType, Long itemId);

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
