package com.example.aichinese.dto.learn;

import java.util.List;

/**
 * What a finished lesson, set or review changed: XP earned now (0 on a repeat), the daily-goal bonus when the goal was
 * reached by it, today's XP against the goal, the streak, the badges earned now ({@code RewardBadge} names) and, when
 * the XP took the learner past a rank threshold, the rank reached ({@code rankUp}, else null).
 */
public record XpResult(
        int xpEarned,
        int bonusXp,
        int todayXp,
        int dailyGoalXp,
        boolean goalReached,
        int currentStreak,
        List<String> newBadges,
        RankUp rankUp) {

    /**
     * A rank reached (every 100 XP opens the next one): its number, the total XP it opens at and the total XP the rank
     * after it opens at.
     */
    public record RankUp(int rank, int rankXp, int nextRankXp) {
    }
}
