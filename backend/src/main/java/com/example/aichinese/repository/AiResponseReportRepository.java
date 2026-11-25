package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.dto.admin.AdminDashboardResponse;
import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

import jakarta.persistence.LockModeType;

public interface AiResponseReportRepository extends JpaRepository<AiResponseReport, Long> {

    @Override
    @EntityGraph(attributePaths = { "studentUser", "reviewerUser" })
    @NonNull
    Optional<AiResponseReport> findById(@NonNull Long id);

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
    @Query("select report from AiResponseReport report where report.id = :id")
    Optional<AiResponseReport> findLockedById(@Param("id") Long id);

    /** Account deletion: plan reports carry the learner's own goal words and SparkBot's personal plan copy. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AiResponseReport report where report.studentUser.id = :studentUserId and report.featureKey = :featureKey")
    int deleteByStudentUserIdAndFeatureKey(
            @Param("studentUserId") Long studentUserId,
            @Param("featureKey") AiFeatureKey featureKey);

    /** Account deletion: remaining reports keep the AI answer for admin review while dropping learner-written text. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AiResponseReport report
            set report.description = null, report.promptSnapshot = null
            where report.studentUser.id = :studentUserId
            """)
    int clearPersonalTextByStudentUserId(@Param("studentUserId") Long studentUserId);
}
