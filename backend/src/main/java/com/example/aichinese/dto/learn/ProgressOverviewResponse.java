package com.example.aichinese.dto.learn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The student's Progress page: XP level, the streak with a month calendar of goal days and the eight badges in catalogue
 * order.
 */
public record ProgressOverviewResponse(
        int totalXp,
        int level,
        int xpIntoLevel,
        int nextLevelXp,
        int currentStreak,
        int bestStreak,
        Calendar calendar,
        List<BadgeView> badges) {

    /**
     * One month ({@code yyyy-MM}) of the streak calendar: the days the goal was reached. Days before {@code joinedOn}
     * are not missed days. {@code previousMonth}/{@code nextMonth} are null at the ends (the joining month, this month).
     */
    public record Calendar(
            String month,
            LocalDate today,
            LocalDate joinedOn,
            List<LocalDate> goalDays,
            String previousMonth,
            String nextMonth) {
    }

    /** {@code key} is the {@code RewardBadge} name. */
    public record BadgeView(String key, boolean earned, LocalDateTime earnedAt) {
    }
}
