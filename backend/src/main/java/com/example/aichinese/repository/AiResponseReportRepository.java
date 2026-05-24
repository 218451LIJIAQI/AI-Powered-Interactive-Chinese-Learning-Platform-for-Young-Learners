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

/**
 * Persistence operations for {@link AiResponseReport} — learner-filed
 * complaints against a specific SparkBot AI reply (e.g. "this answer
 * was inappropriate / hallucinated / off-topic").
 *
 * <p>Every read references the {@link AiResponseReport#GRAPH_ADMIN_DETAIL
 * named entity graph} so the admin moderation queue and the student
 * "my reports" view both render a full row in a single SQL round-trip
 * without N+1 fan-out on studentUser / reviewerUser / aiUsageLog /
 * chatSession / chatMessage. Centralising the eager-fetch attribute set
 * on the entity keeps the repository and the relational shape from
 * drifting whenever a new related field is added.</p>
 *
 * <p>The pessimistic-write {@link #findLockedById(Long)} variant exists
 * for the admin review flow where a single report must transition
 * exactly once from {@code PENDING_REVIEW → IN_REVIEW →
 * RESOLVED|DISMISSED} without a concurrent moderator racing the same
 * status update; pairing it with the same eager-fetch graph lets the
 * service layer render the post-transition payload without re-loading
 * the row.</p>
 *
 * <p>Non-null contract: Spring Data Commons annotates the
 * {@code org.springframework.data.repository} package with
 * {@link org.springframework.lang.NonNullApi @NonNullApi}; the
 * {@code @Override} methods below restate {@link NonNull @NonNull}
 * explicitly so the override matches the parent contract (otherwise
 * the IDE flags the override as weakening it), while purely-derived
 * methods rely on Spring Data's runtime null check and stay
 * unannotated to avoid a cascade of unrelated warnings on legacy
 * callsites that do not yet declare their own nullability.</p>
 */
public interface AiResponseReportRepository extends JpaRepository<AiResponseReport, Long> {

    /**
     * Status-filtered admin moderation page. Backed by
     * {@code idx_ai_response_reports_status} for the filter and a
     * filesort on {@code created_at}; the eager-fetch graph keeps the
     * subsequent row mapping to a single round trip.
     */
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    Page<AiResponseReport> findByStatusOrderByCreatedAtDesc(AiResponseReportStatus status, Pageable pageable);

    /**
     * Student "my reports" page. Backed by the composite index
     * {@code idx_ai_response_reports_student_created}
     * ({@code student_user_id, created_at}) which fully covers the
     * filter-and-order. The eager-fetch graph then attaches studentUser
     * / reviewerUser / aiUsageLog / chatSession / chatMessage.
     */
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    Page<AiResponseReport> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId, Pageable pageable);

    /**
     * Unfiltered admin moderation page (used when the moderator clears
     * the status filter). Inherits the named eager-fetch graph for
     * uniform row hydration with the filtered variant above.
     */
    @Override
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    @NonNull
    Page<AiResponseReport> findAll(@NonNull Pageable pageable);

    /**
     * Hydrated single-row read used by the moderation detail panel and
     * by the post-transition response after a {@link #findLockedById}
     * write. Inherits the named eager-fetch graph.
     */
    @Override
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    @NonNull
    Optional<AiResponseReport> findById(@NonNull Long id);

    /**
     * Pessimistic-write variant of {@link #findById(Long)} for the
     * status-transition flow where two moderators may race the same
     * report. The lock is held for the duration of the surrounding
     * {@code @Transactional} method; the explicit {@code @Query} avoids
     * the {@code SELECT … FOR UPDATE} side-effect that derived
     * {@code findById} would silently apply if the
     * {@code @Lock} annotation were placed on the inherited override.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(value = AiResponseReport.GRAPH_ADMIN_DETAIL)
    @Query("select report from AiResponseReport report where report.id = :id")
    Optional<AiResponseReport> findLockedById(@Param("id") Long id);
}
