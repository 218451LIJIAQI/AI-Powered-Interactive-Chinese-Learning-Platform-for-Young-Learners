package com.example.aichinese.dto.learning;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "totalPoints",
        "currentLevelNumber",
        "progressToNextLevelPercent",
        "streak",
        "badges",
        "recentPoints"
})
public record RewardSummaryResponse(
        int totalPoints,
        int currentLevelNumber,
        int progressToNextLevelPercent,
        DailyStreakResponse streak,
        List<BadgeSummaryResponse> badges,
        List<PointsTransactionResponse> recentPoints) {

    public RewardSummaryResponse {
        if (totalPoints < 0) {
            throw new IllegalArgumentException("totalPoints must be non-negative, was " + totalPoints);
        }
        currentLevelNumber = Math.max(1, currentLevelNumber);
        progressToNextLevelPercent = Math.max(0, Math.min(100, progressToNextLevelPercent));
        badges = badges == null ? List.of() : List.copyOf(badges);
        recentPoints = recentPoints == null ? List.of() : List.copyOf(recentPoints);
    }
}
