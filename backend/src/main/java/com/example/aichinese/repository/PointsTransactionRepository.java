package com.example.aichinese.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PointsTransaction;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {
}
