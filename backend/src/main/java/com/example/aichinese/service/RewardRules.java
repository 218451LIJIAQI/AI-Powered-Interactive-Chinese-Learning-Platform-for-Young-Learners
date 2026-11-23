package com.example.aichinese.service;

import java.time.LocalDate;
import java.util.List;

import com.example.aichinese.entity.enums.PracticeType;

/**
 * Pure XP, level and streak rules, kept side-effect free so they can be unit tested. Learn lessons, Practice sets,
 * the Mistakes review and the daily goal earn XP; classes and the AI Helper never do.
 */
public final class RewardRules {

    public static final int XP_PER_LEVEL = 100;
    private static final List<Integer> DAILY_GOAL_OPTIONS = List.of(10, 20, 30);

    /** Once a day, when today's XP first reaches the daily goal. */
    public static final int DAILY_GOAL_BONUS_XP = 5;
    /** Once a day, for a review that clears at least one mistake. */
    public static final int MISTAKES_REVIEW_XP = 10;

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

    /** XP earned inside the current level, 0 to {@link #XP_PER_LEVEL} - 1. */
    public static int xpIntoLevel(int totalXp) {
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
    public static int accuracyPercent(long correct, long total) {
        return total <= 0 ? 0 : (int) Math.round(correct * 100.0 / total);
    }

    /** The total XP rank {@code rank} opens at (Rank 1 at 0, Rank 2 at 100, ...). */
    public static int rankXp(int rank) {
        return (Math.max(1, rank) - 1) * XP_PER_LEVEL;
    }

    /**
     * The XP a Practice set pays the first time it is done (UI rules): Flashcards and Tracing a fixed +10 at HSK 1 and
     * +15 from HSK 2, Matching +15; Listening, Drag &amp; Drop and Typing per answer (3, 4 and 2 XP) times the items.
     */
    public static int practiceSetXp(PracticeType type, Integer hskLevel, int itemCount) {
        return switch (type) {
            case FLASHCARDS, TRACING -> hskLevel != null && hskLevel >= 2 ? 15 : 10;
            case MATCHING -> 15;
            case LISTENING -> 3 * itemCount;
            case DRAG_DROP -> 4 * itemCount;
            case TYPING -> 2 * itemCount;
        };
    }
}
