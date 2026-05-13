package com.example.aichinese.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.enums.PaymentStatus;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    List<PaymentRecord> findByStudentUserIdOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    Optional<PaymentRecord> findByIdAndStudentUserId(Long id, Long userId);

    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    Optional<PaymentRecord> findById(Long id);

    /**
     * Returns every {@link PaymentRecord} attached to the given order, used by
     * the refund pipeline to mark the related payment row as REFUNDED. Loading
     * the targeted rows directly (instead of pulling every payment record into
     * memory and filtering) keeps refund processing O(1) per order regardless
     * of how many historical payments the platform has recorded.
     */
    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    List<PaymentRecord> findByOrderId(Long orderId);

    @EntityGraph(attributePaths = { "order", "student", "student.user" })
    List<PaymentRecord> findAllByOrderByCreatedAtDesc();

    @Query("select coalesce(sum(pr.amount), 0) from PaymentRecord pr where pr.paymentStatus = :paymentStatus")
    BigDecimal sumAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);

    /**
     * One row per distinct {@code paymentStatus} for the admin analytics
     * dashboard. The previous breakdown loaded the entire payment_record
     * table; this query lets the database use the existing status index
     * directly and only returns at most one row per enum value.
     */
    @Query("select pr.paymentStatus as status, count(pr) as count from PaymentRecord pr group by pr.paymentStatus")
    List<com.example.aichinese.repository.projection.StatusCountProjection> groupByPaymentStatus();
}
