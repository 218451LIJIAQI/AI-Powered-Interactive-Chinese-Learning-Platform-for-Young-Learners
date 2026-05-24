package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.repository.projection.StatusCountProjection;

/**
 * Persistence operations for {@link AiUsageLog} — the immutable audit
 * trail of every AI invocation made by a learner, teacher or system
 * worker.
 *
 * <p>The {@code ai_usage_log} table is the single largest write-heavy
 * table in the platform (one row per AI request, including failures
 * and rate-limit rejections). Every read here is therefore engineered
 * to push filtering and aggregation down to the database — see
 * {@link #groupByAiStatus()} for the admin-analytics breakdown, and
 * the {@code @EntityGraph} on the {@code user} relation for the
 * lookup paths.</p>
 *
 * <p>The status column is stored as a free-form {@code String} (not an
 * enum) because the underlying AI provider's failure-reason vocabulary
 * is open-set; {@link #countByStatusIgnoreCase(String)} accordingly
 * matches case-insensitively to absorb upstream casing drift.</p>
 *
 * <p><b>Known index gap:</b> there is no index on {@code status}, so
 * {@link #countByStatusIgnoreCase(String)} and {@link #groupByAiStatus()}
 * both execute as table scans. The admin dashboard amortises this cost
 * by caching the breakdown for a few seconds; if scan latency grows
 * with traffic, add an index on {@code status} (or a generated lower-
 * cased column with an index) via a Flyway migration and update the
 * {@code @Index} list on {@link AiUsageLog}.</p>
 */
public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long>, JpaSpecificationExecutor<AiUsageLog> {

    /**
     * Case-insensitive {@code SELECT COUNT(*)} of rows whose status
     * matches {@code status}. Used by the admin dashboard "success rate"
     * tile; the case insensitivity absorbs upstream provider casing
     * drift (e.g. {@code "Success"} vs {@code "SUCCESS"}) without
     * forcing every writer to normalise.
     *
     * <p>Currently a table scan — see the class-level Javadoc for the
     * recommended index migration.</p>
     */
    long countByStatusIgnoreCase(String status);

    /**
     * Admin AI-usage page driven by a dynamic {@link Specification}
     * (date range, feature key, user, status). Eagerly fetches the
     * {@code user} association so the rendered admin table row does not
     * trigger N+1 lazy loads on the user's display name; the
     * {@link Specification} is permitted to be {@code null} (which
     * Spring Data interprets as "no extra filters").
     */
    @Override
    @EntityGraph(attributePaths = "user")
    @NonNull
    Page<AiUsageLog> findAll(@Nullable Specification<AiUsageLog> specification, @NonNull Pageable pageable);

    /**
     * IDOR-safe single-row lookup: returns the log only when its
     * {@code user} owns the requested id. Prevents a student from
     * surfacing another student's log by guessing the id. Returns
     * {@link Optional#empty()} when {@code id} does not exist, or when
     * the row is a system-initiated call ({@code user_id IS NULL}, which
     * the JPQL inner-join filters out by construction).
     */
    @EntityGraph(attributePaths = "user")
    @Query("select log from AiUsageLog log where log.id = :id and log.user.id = :userId")
    Optional<AiUsageLog> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * One row per distinct AI status (success / failed / unavailable) for
     * the admin analytics dashboard. {@code ai_usage_log} grows on every
     * AI invocation across the platform, so loading the entire table
     * just to count statuses was the highest-volume hotspot in admin
     * analytics before this {@code GROUP BY} replaced the JVM-side fold.
     * The status column is intentionally not indexed (it has very low
     * cardinality and high write churn); see the class-level Javadoc.
     */
    @Query("select log.status as status, count(log) as count from AiUsageLog log group by log.status")
    List<StatusCountProjection> groupByAiStatus();
}
