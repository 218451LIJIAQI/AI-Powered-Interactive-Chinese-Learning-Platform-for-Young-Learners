package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long>, AiUsageLogRepositoryCustom {

    /** Requests of one feature with one result. */
    record FeatureStatusCount(AiFeatureKey feature, AiRequestStatus status, long count) {
    }

    /** Requests of one user and feature with one result since a time (the plan's "Talk with Xiaomei this week"). */
    boolean existsByUserIdAndFeatureKeyAndStatusAndCreatedAtGreaterThanEqual(
            Long userId, AiFeatureKey featureKey, AiRequestStatus status, LocalDateTime since);

    @Query("""
            select new com.example.aichinese.repository.AiUsageLogRepository$FeatureStatusCount(
                    log.featureKey, log.status, count(log))
            from AiUsageLog log
            group by log.featureKey, log.status
            """)
    List<FeatureStatusCount> countByFeatureAndStatus();
}
