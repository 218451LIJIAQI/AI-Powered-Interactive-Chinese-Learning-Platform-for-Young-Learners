package com.example.aichinese.dto.learning;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Student's learning-streak status, returned by
 * {@code GET /api/v1/students/rewards/streak} and embedded inside
 * {@link RewardSummaryResponse#streak()}.
 *
 * <p>Drives the "N-day streak" indicator on the student
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
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #currentStreak} and {@link #bestStreak} are
 *       non-negative (guarded by the canonical constructor).</li>
 *   <li>{@link #bestStreak} should be {@code &gt;=}
 *       {@link #currentStreak} in steady state. The DTO does not
 *       enforce that relationship because reader-side fixup
 *       (clamping {@code bestStreak} up to {@code currentStreak})
 *       is the gamification service's responsibility — the DTO
 *       must faithfully reflect what was persisted.</li>
 * </ul>
 *
 * @param currentStreak    consecutive days of learning activity
 *                         ending on or before today
 *                         (Asia/Singapore); non-negative
 * @param bestStreak       all-time peak of {@link #currentStreak}
 *                         observed for this student; non-negative
 * @param lastLearningDate calendar date the streak last advanced
 *                         (Asia/Singapore); {@code null} when the
 *                         student has never logged an activity
 */
@JsonPropertyOrder({ "currentStreak", "bestStreak", "lastLearningDate" })
public record DailyStreakResponse(
        int currentStreak,
        int bestStreak,
        LocalDate lastLearningDate) {

    /**
     * Defensive canonical constructor: enforces the non-negative
     * invariant on both streak counters so a regression that forgets
     * to null-coalesce a {@code null} {@link Integer} from the
     * entity (which would auto-unbox to a {@link NullPointerException}
     * the producer might mask) cannot propagate negative values to
     * the front-end gamification widget. {@link #lastLearningDate}
     * is intentionally permitted to be {@code null} for students
     * who have never logged an activity.
     */
    public DailyStreakResponse {
        if (currentStreak < 0) {
            throw new IllegalArgumentException("currentStreak must be non-negative, was " + currentStreak);
        }
        if (bestStreak < 0) {
            throw new IllegalArgumentException("bestStreak must be non-negative, was " + bestStreak);
        }
    }
}
