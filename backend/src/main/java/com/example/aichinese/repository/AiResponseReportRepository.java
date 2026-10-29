package com.example.aichinese.repository;

import java.util.Collection;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

import jakarta.persistence.LockModeType;

public interface AiResponseReportRepository extends JpaRepository<AiResponseReport, Long> {

    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    Page<AiResponseReport> findByStatusInOrderByCreatedAtDescIdDesc(
            Collection<AiResponseReportStatus> statuses, Pageable pageable);

    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    Page<AiResponseReport> findByStudentUserIdOrderByCreatedAtDescIdDesc(Long studentUserId, Pageable pageable);

    /** Every report, open ones first, newest first within each group. */
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    @Query(value = """
            select report from AiResponseReport report
            order by case when report.status = com.example.aichinese.entity.enums.AiResponseReportStatus.PENDING_REVIEW
                          then 0 else 1 end,
                     report.createdAt desc, report.id desc
            """,
            countQuery = "select count(report) from AiResponseReport report")
    Page<AiResponseReport> findAllOpenFirst(Pageable pageable);

    long countByStatus(AiResponseReportStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    Optional<AiResponseReport> findLockedById(Long id);
}
