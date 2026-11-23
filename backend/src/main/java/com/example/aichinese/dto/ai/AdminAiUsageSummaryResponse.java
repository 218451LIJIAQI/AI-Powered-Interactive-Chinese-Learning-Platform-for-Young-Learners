package com.example.aichinese.dto.ai;

import java.util.List;

import com.example.aichinese.entity.enums.AiFeatureKey;

/**
 * All-time AI provider request totals by result, and per-feature request counts in {@link AiFeatureKey} order
 * (zeros included; rule-based {@code HINT} is omitted because it does not invoke a provider).
 */
public record AdminAiUsageSummaryResponse(
        long totalRequests,
        long successful,
        long unavailable,
        long failed,
        List<FeatureCount> byFeature) {

    public record FeatureCount(AiFeatureKey featureKey, long requests) {
    }
}
