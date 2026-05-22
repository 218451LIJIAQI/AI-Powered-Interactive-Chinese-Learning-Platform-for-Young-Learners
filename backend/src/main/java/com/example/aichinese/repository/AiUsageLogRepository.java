package com.example.aichinese.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.entity.AiUsageLog;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long>, JpaSpecificationExecutor<AiUsageLog> {

    long countByStatusIgnoreCase(String status);

    @Override
    @EntityGraph(attributePaths = "user")
    org.springframework.data.domain.Page<AiUsageLog> findAll(
            org.springframework.data.jpa.domain.Specification<AiUsageLog> specification,
            org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("select log from AiUsageLog log where log.id = :id and log.user.id = :userId")
    Optional<AiUsageLog> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * One row per distinct AI status (success / failed / unavailable) for
     * the admin analytics dashboard. ai_usage_log grows on every AI
     * invocation across the platform, so loading the entire table just to
     * count statuses was the highest-volume hotspot in admin analytics
     * before this query existed.
     */
    @org.springframework.data.jpa.repository.Query(
            "select log.status as status, count(log) as count from AiUsageLog log group by log.status")
    List<com.example.aichinese.repository.projection.StatusCountProjection> groupByAiStatus();

    /**
     * Total AI invocations across the platform — replaces
     * {@code findAll().size()} which previously paid a full table load just
     * to count rows.
     */
    @org.springframework.data.jpa.repository.Query("select count(log) from AiUsageLog log")
    long countAll();
}
