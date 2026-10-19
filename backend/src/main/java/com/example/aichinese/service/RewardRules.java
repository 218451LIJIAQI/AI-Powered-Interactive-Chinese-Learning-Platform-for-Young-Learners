package com.example.aichinese.service;

import java.time.LocalDate;

/**
 * Pure reward calculations (FR-PROG-06/07/08), kept side-effect free so they can be unit tested.
 */
public final class RewardRules {

    public static final int POINTS_PER_LEVEL = 100;
    public static final int POINT_STARTER_THRESHOLD = 50;
    public static final int STREAK_EXPLORER_DAYS = 3;

    private RewardRules() {
    }

    /** Reward level: floor(total points / 100) + 1, starting at Level 1. */
    public static int levelFor(int totalPoints) {
        return Math.max(0, totalPoints) / POINTS_PER_LEVEL + 1;
    }

    /** Progress towards the next level, 0-99 (points inside the current level). */
    public static int progressToNextLevelPercent(int totalPoints) {
        return Math.max(0, totalPoints) % POINTS_PER_LEVEL;
    }

    /**
     * Streak after learning on {@code today}: unchanged when the learner already learned today,
     * +1 when the previous learning day was yesterday, otherwise reset to 1.
     */
    public static int nextStreak(LocalDate lastLearningDate, int currentStreak, LocalDate today) {
        if (lastLearningDate == null || currentStreak <= 0) {
            return 1;
        }
        if (lastLearningDate.equals(today)) {
            return currentStreak;
        }
        if (lastLearningDate.plusDays(1).equals(today)) {
            return currentStreak + 1;
        }
        return 1;
    }
}
