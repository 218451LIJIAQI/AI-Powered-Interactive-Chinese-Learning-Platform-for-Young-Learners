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

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long>, JpaSpecificationExecutor<AiUsageLog> {

    long countByStatusIgnoreCase(String status);

    @Override
    @EntityGraph(attributePaths = "user")
    @NonNull
    Page<AiUsageLog> findAll(@Nullable Specification<AiUsageLog> specification, @NonNull Pageable pageable);

    @EntityGraph(attributePaths = "user")
    @Query("select log from AiUsageLog log where log.id = :id and log.user.id = :userId")
    Optional<AiUsageLog> findByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select log.status as status, count(log) as count from AiUsageLog log group by log.status")
    List<StatusCountProjection> groupByAiStatus();
}
