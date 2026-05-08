package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PayoutRequest;
import com.example.aichinese.entity.enums.PayoutStatus;

public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, Long> {

    long countByPayoutStatus(PayoutStatus payoutStatus);
}
