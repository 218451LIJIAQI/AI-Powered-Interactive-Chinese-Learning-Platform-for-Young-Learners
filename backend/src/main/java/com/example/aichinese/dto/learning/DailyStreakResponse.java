package com.example.aichinese.dto.learning;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "currentStreak", "bestStreak", "lastLearningDate" })
public record DailyStreakResponse(
        int currentStreak,
        int bestStreak,
        LocalDate lastLearningDate) {

    public DailyStreakResponse {
        if (currentStreak < 0) {
            throw new IllegalArgumentException("currentStreak must be non-negative, was " + currentStreak);
        }
        if (bestStreak < 0) {
            throw new IllegalArgumentException("bestStreak must be non-negative, was " + bestStreak);
        }
    }
}
