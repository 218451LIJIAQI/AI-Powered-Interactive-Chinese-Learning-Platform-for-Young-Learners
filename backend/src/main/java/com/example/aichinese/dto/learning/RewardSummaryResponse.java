package com.example.aichinese.dto.learning;

import java.util.List;

public record RewardSummaryResponse(
        int totalPoints,
        int currentLevelNumber,
        int progressToNextLevelPercent,
        DailyStreakResponse streak,
        List<BadgeSummaryResponse> badges,
        List<PointsTransactionResponse> recentPoints) {
}
