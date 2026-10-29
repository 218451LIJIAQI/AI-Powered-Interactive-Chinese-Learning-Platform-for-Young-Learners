package com.example.aichinese.dto.ai;

import java.util.List;

/** All-time AI request totals by result, and the requests of every feature (in AiFeatureKey order, zeros included). */
public record AdminAiUsageSummaryResponse(
        long totalRequests,
        long successful,
        long unavailable,
        long failed,
        List<FeatureCount> byFeature) {

    public AdminAiUsageSummaryResponse {
        byFeature = byFeature == null ? List.of() : List.copyOf(byFeature);
    }

    public record FeatureCount(String featureKey, long requests) {
    }
}
