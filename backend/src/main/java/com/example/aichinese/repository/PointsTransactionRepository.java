package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.PointsTransaction;

public interface PointsTransactionRepository extends JpaRepository<PointsTransaction, Long> {

    List<PointsTransaction> findByStudentIdOrderByCreatedAtDesc(Long studentId);

    Page<PointsTransaction> findByStudentIdOrderByCreatedAtDesc(Long studentId, Pageable pageable);

    boolean existsByStudentIdAndSourceTypeAndSourceId(Long studentId, String sourceType, Long sourceId);

    java.util.Optional<PointsTransaction> findByStudentIdAndSourceTypeAndSourceId(Long studentId, String sourceType, Long sourceId);
}
