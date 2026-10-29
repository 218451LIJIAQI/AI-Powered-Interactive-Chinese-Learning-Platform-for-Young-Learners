package com.example.aichinese.dto.learn;

import java.util.List;

/**
 * What a finished lesson, set or review changed: XP earned now (0 on a repeat), the daily-goal bonus when the goal was
 * reached by it, today's XP against the goal, the streak and the badges earned now ({@code RewardBadge} names).
 */
public record XpResult(
        int xpEarned,
        int bonusXp,
        int totalXp,
        int todayXp,
        int dailyGoalXp,
        boolean goalReached,
        boolean goalJustReached,
        int currentStreak,
        List<String> newBadges) {

    public XpResult {
        newBadges = newBadges == null ? List.of() : List.copyOf(newBadges);
    }
}
