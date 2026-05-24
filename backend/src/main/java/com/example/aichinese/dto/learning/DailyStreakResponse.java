package com.example.aichinese.dto.learning;

import java.time.LocalDate;

/**
 * Student's learning-streak status, returned by
 * {@code GET /api/v1/students/rewards/streak} and embedded inside
 * {@link RewardSummaryResponse#streak()}.
 *
 * <p>Drives the "🔥 N-day streak" indicator on the student
 * dashboard. The streak counter increments whenever the student
 * completes any learning activity on a new calendar day
 * (Asia/Singapore timezone) and resets to zero after one missed
 * day.</p>
 *
 * <p>{@link #currentStreak} is the active streak length in days
 * (zero on the first day or right after a break);
 * {@link #bestStreak} is the all-time peak preserved across
 * resets so the front-end can show "Best: 14 days" alongside the
 * current count.</p>
 *
 * <p>{@link #lastLearningDate} is the most recent date the streak
 * advanced (a {@link LocalDate}, not a timestamp, because the
 * streak operates at calendar-day granularity); {@code null} when
 * the student has never logged an activity.</p>
 *
 * <p>Primitive {@code int} for both counters so the JSON wire
 * format always emits a numeric value.</p>
 */
public record DailyStreakResponse(
        int currentStreak,
        int bestStreak,
        LocalDate lastLearningDate) {
}
