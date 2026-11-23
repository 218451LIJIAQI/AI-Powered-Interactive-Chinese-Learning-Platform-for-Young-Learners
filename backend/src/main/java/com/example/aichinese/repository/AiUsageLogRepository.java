package com.example.aichinese.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    List<AiUsageLog> findByUserId(Long userId);

    /** Requests of one feature with one result. */
    record FeatureStatusCount(AiFeatureKey feature, AiRequestStatus status, long count) {
    }

    /** Requests of one user and feature with one result since a time (the plan's "Talk with Xiaomei this week"). */
    boolean existsByUserIdAndFeatureKeyAndStatusAndCreatedAtGreaterThanEqual(
            Long userId, AiFeatureKey featureKey, AiRequestStatus status, LocalDateTime since);

    String ADMIN_FILTERS = """
            where (:feature is null or log.featureKey = :feature)
              and (:status is null or log.status = :status)
              and (:role is null or log.userRole = :role)
            """;
    String ADMIN_ROWS = """
            select new com.example.aichinese.dto.ai.AdminAiUsageLogResponse(
                log.id, coalesce(u.displayName, ''), log.userRole, log.featureKey, log.status, log.errorSummary, log.createdAt)
            from AiUsageLog log left join log.user u
            """ + ADMIN_FILTERS;

    @Query(value = ADMIN_ROWS, countQuery = "select count(log) from AiUsageLog log " + ADMIN_FILTERS)
    Page<AdminAiUsageLogResponse> findAdminPage(@Param("feature") AiFeatureKey feature,
            @Param("status") AiRequestStatus status, @Param("role") String role, Pageable page);

    /** Same rows as the admin page. Export only needs the capped result, so it skips the total-count query. */
    @Query(ADMIN_ROWS)
    List<AdminAiUsageLogResponse> findExportRows(@Param("feature") AiFeatureKey feature,
            @Param("status") AiRequestStatus status, @Param("role") String role, Pageable limit);

    @Query("""
            select new com.example.aichinese.repository.AiUsageLogRepository$FeatureStatusCount(
                    log.featureKey, log.status, count(log))
            from AiUsageLog log
            group by log.featureKey, log.status
            """)
    List<FeatureStatusCount> countByFeatureAndStatus();
}
