package com.example.aichinese.dto.learning;

import java.time.LocalDate;

public record DailyStreakResponse(
        int currentStreak,
        int bestStreak,
        LocalDate lastLearningDate) {
}
