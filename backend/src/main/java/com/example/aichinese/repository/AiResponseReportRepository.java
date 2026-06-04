package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

import jakarta.persistence.LockModeType;

public interface AiResponseReportRepository extends JpaRepository<AiResponseReport, Long> {

    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    Page<AiResponseReport> findByStatusOrderByCreatedAtDesc(AiResponseReportStatus status, Pageable pageable);

    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    Page<AiResponseReport> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId, Pageable pageable);

    @Override
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    @NonNull
    Page<AiResponseReport> findAll(@NonNull Pageable pageable);

    @Override
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    @NonNull
    Optional<AiResponseReport> findById(@NonNull Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    @Query("select report from AiResponseReport report where report.id = :id")
    Optional<AiResponseReport> findLockedById(@Param("id") Long id);
}
