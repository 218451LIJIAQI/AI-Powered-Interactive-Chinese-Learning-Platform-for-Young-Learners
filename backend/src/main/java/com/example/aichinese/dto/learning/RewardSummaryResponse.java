package com.example.aichinese.dto.learning;

/** What the student sidebar shows: total XP and the current streak (days the daily goal was reached). */
public record RewardSummaryResponse(int totalPoints, int currentStreak) {
}
