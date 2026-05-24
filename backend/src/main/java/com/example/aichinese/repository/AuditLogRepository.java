package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop5ByOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    @NonNull
    List<AuditLog> findAll();

    /**
     * Sort-aware variant for the admin audit log view so ordering by
     * {@code createdAt DESC} is delegated to the database instead of being
     * re-sorted in JVM memory after a full table load. The audit_log table
     * grows on every privileged action and this was one of the more
     * expensive admin endpoints under load.
     */
    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    @NonNull
    List<AuditLog> findAll(@NonNull Sort sort);

    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    @NonNull
    Page<AuditLog> findAll(@Nullable org.springframework.data.jpa.domain.Specification<AuditLog> specification, @NonNull Pageable pageable);

}
