package com.example.aichinese.dto.learn;

import jakarta.validation.constraints.NotNull;

/** {@code PUT /students/daily-goal}: 10, 20 or 30 XP a day. */
public record DailyGoalRequest(
        @NotNull(message = "Daily goal must be 10, 20 or 30 XP.")
        Integer dailyGoalXp) {
}
