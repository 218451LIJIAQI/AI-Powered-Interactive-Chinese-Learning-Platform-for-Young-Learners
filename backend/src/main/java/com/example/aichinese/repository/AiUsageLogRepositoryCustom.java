package com.example.aichinese.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.example.aichinese.dto.ai.AdminAiUsageLogResponse;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;

public interface AiUsageLogRepositoryCustom {

    Page<AdminAiUsageLogResponse> findAdminPage(
            AiFeatureKey feature, AiRequestStatus status, String role, Pageable page);

    /** Same rows as the admin page. Export only needs the capped result, so it skips the total-count query. */
    List<AdminAiUsageLogResponse> findExportRows(
            AiFeatureKey feature, AiRequestStatus status, String role, Pageable limit);
}
