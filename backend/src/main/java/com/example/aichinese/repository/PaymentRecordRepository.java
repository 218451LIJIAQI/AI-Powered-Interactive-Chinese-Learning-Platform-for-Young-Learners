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

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    Page<PaymentRecord> findByStudentUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    Optional<PaymentRecord> findByIdAndStudentUserId(Long id, Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @Query("select pr from PaymentRecord pr where pr.id = :id and pr.student.user.id = :userId")
    Optional<PaymentRecord> findLockedByIdAndStudentUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Override
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @NonNull
    Optional<PaymentRecord> findById(@NonNull Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @Query("select pr from PaymentRecord pr where pr.id = :id")
    Optional<PaymentRecord> findLockedById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @Query("select pr from PaymentRecord pr where pr.providerName = :providerName and pr.providerReference = :providerReference")
    Optional<PaymentRecord> findLockedByProviderNameAndProviderReference(
            @Param("providerName") String providerName,
            @Param("providerReference") String providerReference);

    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    List<PaymentRecord> findByOrderId(Long orderId);

    @Override
    @EntityGraph(value = PaymentRecord.GRAPH_DETAIL)
    @NonNull
    Page<PaymentRecord> findAll(@NonNull Pageable pageable);

    @Query("select coalesce(sum(pr.amount), 0) from PaymentRecord pr where pr.paymentStatus = :paymentStatus")
    BigDecimal sumAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);

    @Query("select pr.paymentStatus as status, count(pr) as count from PaymentRecord pr group by pr.paymentStatus")
    List<StatusCountProjection> groupByPaymentStatus();
}
