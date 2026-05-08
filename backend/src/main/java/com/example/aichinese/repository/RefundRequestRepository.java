package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.RefundRequest;
import com.example.aichinese.entity.enums.RefundStatus;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {

    long countByRefundStatus(RefundStatus refundStatus);
}
