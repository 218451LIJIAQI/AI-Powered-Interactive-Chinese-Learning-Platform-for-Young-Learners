package com.example.aichinese.service;

import java.time.LocalDate;
import java.util.List;

/**
 * Pure XP, level and streak rules, kept side-effect free so they can be unit tested. Learn lessons, Practice sets,
 * the Mistakes review and the daily goal earn XP; classes and the AI Helper never do.
 */
public final class RewardRules {

    public static final int XP_PER_LEVEL = 100;
    public static final List<Integer> DAILY_GOAL_OPTIONS = List.of(10, 20, 30);
    public static final int DEFAULT_DAILY_GOAL_XP = 20;

    /** Once a day, when today's XP first reaches the daily goal. */
    public static final int DAILY_GOAL_BONUS_XP = 5;
    /** Once a day, for a review that clears at least one mistake. */
    public static final int MISTAKES_REVIEW_XP = 10;
    /** Once per quiz set, the first time a finished run has 3 right answers in a row. */
    public static final int QUIZ_STREAK_BONUS_XP = 5;
    public static final int QUIZ_STREAK_LENGTH = 3;

    public static final int STREAK_EXPLORER_DAYS = 3;
    public static final int TEN_DAY_STREAK_DAYS = 10;
    public static final int XP_STARTER_THRESHOLD = 50;
    public static final int XP_300_THRESHOLD = 300;
    public static final int LESSON_FINISHER_COUNT = 5;
    public static final int QUIZ_STAR_PERCENT = 80;
    /** A level checkpoint is passed at 70% or more. */
    public static final int CHECKPOINT_PASS_PERCENT = 70;

    private RewardRules() {
    }

    /** XP level: floor(total XP / 100) + 1, starting at Level 1. */
    public static int levelFor(int totalXp) {
        return Math.max(0, totalXp) / XP_PER_LEVEL + 1;
    }

    /** Progress towards the next level, 0-99 (XP inside the current level). */
    public static int progressToNextLevelPercent(int totalXp) {
        return Math.max(0, totalXp) % XP_PER_LEVEL;
    }

    public static boolean isDailyGoalOption(Integer xp) {
        return xp != null && DAILY_GOAL_OPTIONS.contains(xp);
    }

    /**
     * Streak after the daily goal is reached on {@code today}: unchanged when it was already reached today, +1 when
     * it was last reached yesterday, otherwise a new streak of 1.
     */
    public static int nextStreak(LocalDate lastGoalDate, int currentStreak, LocalDate today) {
        if (lastGoalDate == null || currentStreak <= 0) {
            return 1;
        }
        if (lastGoalDate.equals(today)) {
            return currentStreak;
        }
        if (lastGoalDate.plusDays(1).equals(today)) {
            return currentStreak + 1;
        }
        return 1;
    }

    /**
     * The streak as it stands today: the stored streak while the goal was last reached today or yesterday (today can
     * still keep it), otherwise 0 (a day was missed).
     */
    public static int currentStreak(LocalDate lastGoalDate, int storedStreak, LocalDate today) {
        if (lastGoalDate == null || storedStreak <= 0) {
            return 0;
        }
        return lastGoalDate.equals(today) || lastGoalDate.plusDays(1).equals(today) ? storedStreak : 0;
    }

    /** Percentage of right answers, rounded; 0 for no questions. */
    public static int accuracyPercent(int correct, int total) {
        return total <= 0 ? 0 : (int) Math.round(correct * 100.0 / total);
    }

    /** The longest run of consecutive right answers. */
    public static int longestRun(List<Boolean> answersInOrder) {
        int longest = 0;
        int run = 0;
        for (Boolean correct : answersInOrder) {
            run = Boolean.TRUE.equals(correct) ? run + 1 : 0;
            longest = Math.max(longest, run);
        }
        return longest;
    }
}
