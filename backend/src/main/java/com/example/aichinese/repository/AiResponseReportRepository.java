package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.enums.AiResponseReportStatus;

import jakarta.persistence.LockModeType;

/**
 * Persistence operations for {@link AiResponseReport} — learner-filed
 * complaints against a specific SparkBot AI reply (e.g. "this answer
 * was inappropriate / hallucinated / off-topic").
 *
 * <p>Every read eager-fetches studentUser / reviewerUser / aiUsageLog /
 * chatSession / chatMessage so the admin moderation queue can render a
 * full report row in one round-trip. The pessimistic-write
 * {@link #findLockedById(Long)} variant exists for the admin review
 * flow where a single report must transition exactly once from
 * {@code OPEN → IN_REVIEW → RESOLVED} without a concurrent moderator
 * racing the same status update.</p>
 */
public interface AiResponseReportRepository extends JpaRepository<AiResponseReport, Long> {

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    @NonNull
    List<AiResponseReport> findAll(@NonNull Sort sort);

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    List<AiResponseReport> findByStatusOrderByCreatedAtDesc(AiResponseReportStatus status);

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    Page<AiResponseReport> findByStatusOrderByCreatedAtDesc(AiResponseReportStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    List<AiResponseReport> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId);

    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    Page<AiResponseReport> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    @NonNull
    Page<AiResponseReport> findAll(@NonNull Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    @NonNull
    Optional<AiResponseReport> findById(@NonNull Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {
            "studentUser",
            "reviewerUser",
            "aiUsageLog",
            "chatSession",
            "chatMessage"
    })
    @Query("select report from AiResponseReport report where report.id = :id")
    Optional<AiResponseReport> findLockedById(@Param("id") Long id);
}
