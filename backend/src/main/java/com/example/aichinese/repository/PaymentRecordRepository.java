package com.example.aichinese.repository;

import java.math.BigDecimal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.PaymentRecord;
import com.example.aichinese.entity.enums.PaymentStatus;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {

    long countByPaymentStatus(PaymentStatus paymentStatus);

    @Query("select coalesce(sum(pr.amount), 0) from PaymentRecord pr where pr.paymentStatus = :paymentStatus")
    BigDecimal sumAmountByPaymentStatus(@Param("paymentStatus") PaymentStatus paymentStatus);
}
