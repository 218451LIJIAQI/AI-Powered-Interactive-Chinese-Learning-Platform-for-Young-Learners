package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.dto.admin.AdminDashboardResponse;
import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

import jakarta.persistence.LockModeType;

public interface AiResponseReportRepository extends JpaRepository<AiResponseReport, Long> {

    @Query(value = """
            select new com.example.aichinese.dto.admin.AdminDashboardResponse$AiReportItem(
                    report.id, report.reasonCategory, report.studentUser.displayName, report.createdAt)
            from AiResponseReport report where report.status = :status
            order by report.createdAt desc, report.id desc
            """, countQuery = "select count(report) from AiResponseReport report where report.status = :status")
    Page<AdminDashboardResponse.AiReportItem> findDashboardItems(@Param("status") AiResponseReportStatus status,
            Pageable pageable);

    Page<AiResponseReport> findByStudentUserIdOrderByCreatedAtDescIdDesc(Long studentUserId, Pageable pageable);

    /** Every report, open ones first, newest first within each group. */
    @EntityGraph(attributePaths = { "studentUser", "reviewerUser" })
    @Query(value = """
            select report from AiResponseReport report
            order by case when report.status = com.example.aichinese.entity.enums.AiResponseReportStatus.PENDING_REVIEW
                          then 0 else 1 end,
                     report.createdAt desc, report.id desc
            """,
            countQuery = "select count(report) from AiResponseReport report")
    Page<AiResponseReport> findAllOpenFirst(Pageable pageable);

    long countByStatus(AiResponseReportStatus status);

    @Query("select report.id from AiResponseReport report where report.studentUser.id = :studentUserId")
    List<Long> findIdsByStudentUserId(@Param("studentUserId") Long studentUserId);

    /**
     * The report, locked alone: a join would also lock the joined rows, the learner's and the last reviewer's accounts,
     * and hold up their own account writes (a token refresh, an AI request) until the review ends.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AiResponseReport> findLockedById(Long id);
}
