package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;

import com.example.aichinese.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

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

    /**
     * Lookup used by the audit-log invariant tests to verify that a given
     * privileged action produced an audit row. Filtering on
     * {@code (actionType, targetType, targetId)} matches the natural key
     * of an audit record without requiring callers to scan the full table.
     */
    boolean existsByActionTypeAndTargetTypeAndTargetId(
            String actionType,
            String targetType,
            Long targetId);
}
