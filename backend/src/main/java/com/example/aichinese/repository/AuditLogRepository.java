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
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.AuditLog;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {

    @Override
    @EntityGraph(attributePaths = { "actorUser" })
    @NonNull
    Page<AuditLog> findAll(@Nullable Specification<AuditLog> specification, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = { "actorUser" })
    Optional<AuditLog> findDetailedById(Long id);

    /** Every action type that occurs in the log, for the audit log's Action filter. */
    @Query("select distinct a.actionType from AuditLog a order by a.actionType")
    List<String> findDistinctActionTypes();

    /** Entries about one target, oldest first (a teacher application's history). */
    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findByTargetTypeAndTargetIdAndActionTypeInOrderByCreatedAtAsc(
            String targetType, Long targetId, List<String> actionTypes);

    /** The latest entries about one target, newest first (a practice set's history). */
    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc(String targetType, Long targetId);

    /** The latest entry of one action about one target (the reason of an account's current deactivation). */
    @EntityGraph(attributePaths = { "actorUser" })
    Optional<AuditLog> findFirstByTargetTypeAndTargetIdAndActionTypeOrderByCreatedAtDescIdDesc(
            String targetType, Long targetId, String actionType);
}
