package com.example.aichinese.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /** The latest entries about one target, newest first. */
    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(
            String targetType, Long targetId, Pageable pageable);

    /** The latest entries about one target matching specific actions, newest first. */
    @EntityGraph(attributePaths = { "actorUser" })
    List<AuditLog> findByTargetTypeAndTargetIdAndActionTypeInOrderByCreatedAtDescIdDesc(
            String targetType, Long targetId, Collection<String> actionTypes, Pageable pageable);

    /** The latest entry of one action about one target (the reason of an account's current deactivation). */
    @EntityGraph(attributePaths = { "actorUser" })
    Optional<AuditLog> findFirstByTargetTypeAndTargetIdAndActionTypeOrderByCreatedAtDescIdDesc(
            String targetType, Long targetId, String actionType);

    /** Status transitions only, newest first by monotonic id, independent of clock skew or unrelated events since. */
    @Query("""
            select a.actionType from AuditLog a
            where a.targetType = :targetType and a.targetId = :targetId and a.actionType in :actions
            order by a.id desc
            """)
    List<String> findRecentActions(@Param("targetType") String targetType, @Param("targetId") Long targetId,
            @Param("actions") Collection<String> actions, Pageable pageable);

    /** The latest entry of one action (the platform settings' "last changed"). */
    @EntityGraph(attributePaths = { "actorUser" })
    Optional<AuditLog> findFirstByActionTypeOrderByCreatedAtDescIdDesc(String actionType);

    // Account deletion: the only edit an entry ever gets is losing a deleted account's personal data.

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuditLog a set a.actorEmail = :anonymousEmail where a.actorUser.id = :userId")
    int anonymiseActorEmail(@Param("userId") Long userId, @Param("anonymousEmail") String anonymousEmail);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update AuditLog a set a.targetLabel = :anonymousLabel where a.targetType = :targetType and a.targetId = :targetId")
    int anonymiseTargetLabel(
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("anonymousLabel") String anonymousLabel);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuditLog a set a.targetLabel = :anonymousLabel
            where a.targetType = :targetType and lower(a.targetLabel) = lower(:email)
            """)
    int anonymiseTargetLabelByEmail(
            @Param("targetType") String targetType,
            @Param("email") String email,
            @Param("anonymousLabel") String anonymousLabel);

    /**
     * Replaces every support-request audit target label for {@code requestIds} with {@code #<number>} in one statement.
     * Must run before those {@code SupportRequest} rows are deleted.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AuditLog a
            set a.targetLabel = concat('#', (select s.number from SupportRequest s where s.id = a.targetId))
            where a.targetType = :targetType and a.targetId in :requestIds
            """)
    int anonymiseSupportTargetLabels(
            @Param("targetType") String targetType,
            @Param("requestIds") Collection<Long> requestIds);
}
