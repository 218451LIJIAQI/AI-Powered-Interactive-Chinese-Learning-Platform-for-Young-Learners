package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PaymentRecord;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, Long> {
}
