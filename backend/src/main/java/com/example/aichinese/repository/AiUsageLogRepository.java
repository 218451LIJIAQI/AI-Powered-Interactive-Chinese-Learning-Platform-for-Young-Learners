package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.repository.projection.StatusCountProjection;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long>, JpaSpecificationExecutor<AiUsageLog> {

    /** Requests of one feature with one result. */
    interface FeatureStatusCount {

        AiFeatureKey getFeatureKey();

        String getStatus();

        long getCount();
    }

    /** Requests of one user and feature with one result since a time (the plan's "Talk with Xiaomei this week"). */
    long countByUserIdAndFeatureKeyAndStatusAndCreatedAtGreaterThanEqual(
            Long userId, AiFeatureKey featureKey, String status, LocalDateTime since);

    @Override
    @EntityGraph(attributePaths = "user")
    @NonNull
    Page<AiUsageLog> findAll(@Nullable Specification<AiUsageLog> specification, @NonNull Pageable pageable);

    @Query("select log.status as status, count(log) as count from AiUsageLog log group by log.status")
    List<StatusCountProjection> groupByAiStatus();

    @Query("""
            select log.featureKey as featureKey, log.status as status, count(log) as count
            from AiUsageLog log
            group by log.featureKey, log.status
            """)
    List<FeatureStatusCount> countByFeatureAndStatus();
}
