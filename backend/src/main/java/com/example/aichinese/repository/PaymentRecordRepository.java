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

    Optional<PaymentRecord> findByProviderReference(String providerReference);

    @Query("select coalesce(sum(pr.amount), 0) from PaymentRecord pr where pr.paymentStatus = :paymentStatus")
    BigDecimal sumAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);
}
