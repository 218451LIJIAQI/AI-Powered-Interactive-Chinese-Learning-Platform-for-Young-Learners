package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.AuditLog;

/**
 * Persistence operations for {@link AuditLog} — the append-only ledger
 * of every privileged platform action (auth events, content moderation,
 * payment lifecycle changes, role updates, etc.).
 *
 * <p>Every method eager-fetches {@code actorUser} so the admin audit
 * view renders the responsible user without an extra round-trip per
 * row. The audit table grows on every protected action, so the
 * {@link JpaSpecificationExecutor} {@link Page}/{@link Sort} variants
 * push filtering and ordering down to the database — never load the
 * full table just to slice / sort.</p>
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    /**
     * Five most recent audit rows for the admin dashboard "recent
     * activity" tile. Backed by {@code idx_audit_logs_created_at} which
     * fully covers the {@code ORDER BY created_at DESC LIMIT 5} access
     * pattern; the eager-fetch on {@code actorUser} keeps the rendered
     * tile to a single round trip.
     */
    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop5ByOrderByCreatedAtDesc();

    /**
     * Ten most recent audit rows scoped to a specific target (e.g. a
     * {@code TeacherProfile} row's history panel). Backed by the
     * composite index {@code idx_audit_logs_target_created}
     * ({@code target_type, target_id, created_at}) which covers the
     * filter-and-order in a single index seek.
     */
    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    /**
     * Eager-fetch override of the inherited {@link JpaRepository#findAll()}
     * — used by integration tests that assert specific
     * {@code action_type} entries were emitted (the @EntityGraph keeps
     * those assertions from triggering N+1 lazy loads on actorUser).
     * Production code paths page through
     * {@link #findAll(Specification, Pageable)} rather than this
     * unbounded variant.
     */
    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    @NonNull
    List<AuditLog> findAll();

    /**
     * Admin audit-log page driven by a dynamic {@link Specification}
     * (action type, target type, actor email, date range). The
     * {@code Specification} parameter is permitted to be {@code null}
     * ("no extra filters"); pagination + ordering happen entirely in
     * SQL so the query stays O(page size) regardless of total table
     * size, which matters because audit_log grows on every privileged
     * action across the platform.
     */
    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    @NonNull
    Page<AuditLog> findAll(@Nullable Specification<AuditLog> specification, @NonNull Pageable pageable);
}
