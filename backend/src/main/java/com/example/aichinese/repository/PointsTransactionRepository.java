package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PointsTransaction;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    List<PointsTransaction> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    boolean existsByStudentIdAndSourceTypeAndSourceId(Long studentId, String sourceType, Long sourceId);
}
