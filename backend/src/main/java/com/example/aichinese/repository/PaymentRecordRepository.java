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

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    List<PaymentRecord> findByStudentUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    Page<PaymentRecord> findByStudentUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    Optional<PaymentRecord> findByIdAndStudentUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    @Query("select pr from PaymentRecord pr where pr.id = :id and pr.student.user.id = :userId")
    Optional<PaymentRecord> findLockedByIdAndStudentUserId(@Param("id") Long id, @Param("userId") Long userId);

    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    @NonNull
    Optional<PaymentRecord> findById(@NonNull Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    @Query("select pr from PaymentRecord pr where pr.id = :id")
    Optional<PaymentRecord> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    @Query("select pr from PaymentRecord pr where pr.providerName = :providerName and pr.providerReference = :providerReference")
    Optional<PaymentRecord> findLockedByProviderNameAndProviderReference(
            @Param("providerName") String providerName,
            @Param("providerReference") String providerReference);

    /**
     * Returns every {@link PaymentRecord} attached to the given order, used by
     * the refund pipeline to mark the related payment row as REFUNDED. Loading
     * the targeted rows directly (instead of pulling every payment record into
     * memory and filtering) keeps refund processing O(1) per order regardless
     * of how many historical payments the platform has recorded.
     */
    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    List<PaymentRecord> findByOrderId(Long orderId);

    @Override
    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    @NonNull
    Page<PaymentRecord> findAll(@NonNull Pageable pageable);

    @Query("select coalesce(sum(pr.amount), 0) from PaymentRecord pr where pr.paymentStatus = :paymentStatus")
    BigDecimal sumAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);

    /**
     * One row per distinct {@code paymentStatus} for the admin analytics
     * dashboard. The previous breakdown loaded the entire payment_record
     * table; this query lets the database use the existing status index
     * directly and only returns at most one row per enum value.
     */
    @Query("select pr.paymentStatus as status, count(pr) as count from PaymentRecord pr group by pr.paymentStatus")
    List<StatusCountProjection> groupByPaymentStatus();
}
