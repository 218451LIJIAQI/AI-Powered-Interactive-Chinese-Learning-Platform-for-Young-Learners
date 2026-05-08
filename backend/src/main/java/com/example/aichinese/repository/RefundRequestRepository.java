package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.RefundRequest;

public interface RefundRequestRepository extends JpaRepository<RefundRequest, Long> {
}
