package com.example.aichinese.service.navigation;

import java.util.Map;

import org.springframework.stereotype.Component;

import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiResponseReportRepository;

/** The admin sidebar's AI badge: reports waiting for an admin. */
@Component
public class AiReportNavCountContributor implements NavCountContributor {

    private final AiResponseReportRepository aiResponseReportRepository;

    public AiReportNavCountContributor(AiResponseReportRepository aiResponseReportRepository) {
        this.aiResponseReportRepository = aiResponseReportRepository;
    }

    @Override
    public RoleName role() {
        return RoleName.ADMIN;
    }

    @Override
    public Map<String, Long> counts(Long userId) {
        return Map.of("aiReports", aiResponseReportRepository.countByStatus(AiResponseReportStatus.PENDING_REVIEW));
    }
}
