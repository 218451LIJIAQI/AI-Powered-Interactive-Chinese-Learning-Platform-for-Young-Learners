package com.example.aichinese.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.enums.PaymentStatus;
import com.example.aichinese.repository.projection.StatusCountProjection;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link PaymentRecord} — the immutable
 * audit trail of every payment-provider interaction attached to an
 * {@link com.example.aichinese.entity.Order}.
 *
 * <p>Read-paths consistently eager-fetch {@code order} +
 * {@code student.user} so the response mapper never regresses into
 * lazy lookups for the (frequent) admin / student payment-history
 * views. Write-paths use {@link Lock pessimistic locks} on the
 * {@code (providerName, providerReference)} natural key to keep the
 * webhook-vs-poll race that triggers payment finalisation from
 * double-applying the {@code PAID} transition.</p>
 */
public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    /**
     * Backs the admin dashboard's payment-status tiles (e.g. "Total
     * PAID"). Backed by {@code idx_payment_records_status}.
     */
    long countByPaymentStatus(PaymentStatus paymentStatus);

    /**
     * Paged student "my payments" history. Backed by the composite
     * index on {@code (student.user_id, created_at)}.
     */
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    Page<PaymentRecord> findByStudentUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * IDOR-safe single-row lookup that traverses
     * {@code PaymentRecord → StudentProfile → User}. The service layer
     * maps an empty {@link Optional} to a 404 to keep cross-user payment
     * ids undisclosed.
     */
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    Optional<PaymentRecord> findByIdAndStudentUserId(Long id, Long userId);

    /**
     * Pessimistic-write IDOR-safe variant used by the synchronous
     * payment verification flow (the student polling endpoint that
     * mirrors what the Stripe webhook does asynchronously). Pairs with
     * the entity's {@code @Version} field for defence-in-depth so the
     * student poll and the webhook handler cannot both write
     * inconsistent statuses.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @Query("select pr from PaymentRecord pr where pr.id = :id and pr.student.user.id = :userId")
    Optional<PaymentRecord> findLockedByIdAndStudentUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * Eager-fetch override of the inherited single-row read — used by
     * admin moderation, integration tests and the post-transition
     * mapper after a refund / cancel.
     */
    @Override
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @NonNull
    Optional<PaymentRecord> findById(@NonNull Long id);

    /**
     * Pessimistic-write variant used by the webhook entry point so the
     * webhook handler holds the row lock while it grants entitlements,
     * emits notifications, and flips the status; the unique constraint
     * on {@code (provider_name, provider_reference)} makes the lookup a
     * single-row index seek.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @Query("select pr from PaymentRecord pr where pr.id = :id")
    Optional<PaymentRecord> findLockedById(@Param("id") Long id);

    /**
     * Looks up by the natural key emitted by the payment provider
     * (e.g. Stripe checkout session id). Backed by the unique
     * constraint {@code uk_payment_records_provider_reference} which
     * makes Stripe webhook delivery idempotent: re-receiving the same
     * event finds the existing row instead of inserting a duplicate.
     * Held under a pessimistic write lock so the webhook handler can
     * race-safely complete the order-finalisation transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @Query("select pr from PaymentRecord pr where pr.providerName = :providerName and pr.providerReference = :providerReference")
    Optional<PaymentRecord> findLockedByProviderNameAndProviderReference(
            @Param("providerName") String providerName,
            @Param("providerReference") String providerReference);

    /**
     * Returns every {@link PaymentRecord} attached to the given order,
     * used by the refund pipeline to mark the related payment row as
     * REFUNDED. Loading the targeted rows directly (instead of pulling
     * every payment record into memory and filtering) keeps refund
     * processing O(1) per order regardless of how many historical
     * payments the platform has recorded.
     */
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    List<PaymentRecord> findByOrderId(Long orderId);

    /**
     * Admin payments page. The named entity graph keeps the response
     * mapper from triggering one lazy SELECT per row when assembling
     * the admin payment table.
     */
    @Override
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @NonNull
    Page<PaymentRecord> findAll(@NonNull Pageable pageable);

    /**
     * Sums the gross amount across every payment in the given status
     * (typically {@code PAID}) for the admin revenue tile. The
     * {@code coalesce(..., 0)} wrapper avoids returning {@code null}
     * when no payments yet match, so callers can treat the result as
     * a non-null BigDecimal.
     */
    @Query("select coalesce(sum(pr.amount), 0) from PaymentRecord pr where pr.paymentStatus = :paymentStatus")
    BigDecimal sumAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);

    /**
     * One row per distinct {@code paymentStatus} for the admin analytics
     * dashboard. The previous breakdown loaded the entire payment_record
     * table; this query lets the database use {@code idx_payment_records_status}
     * directly and only returns at most one row per enum value.
     */
    @Query("select pr.paymentStatus as status, count(pr) as count from PaymentRecord pr group by pr.paymentStatus")
    List<StatusCountProjection> groupByPaymentStatus();
}
