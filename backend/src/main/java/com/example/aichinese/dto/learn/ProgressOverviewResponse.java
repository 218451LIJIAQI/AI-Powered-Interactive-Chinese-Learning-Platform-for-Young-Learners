package com.example.aichinese.dto.learn;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * The student's Progress page: XP level, today against the daily goal, the streak with a month calendar of goal days,
 * XP by source, recent XP and the eight badges in catalogue order.
 */
public record ProgressOverviewResponse(
        int totalXp,
        int level,
        int xpIntoLevel,
        int nextLevelXp,
        int dailyGoalXp,
        int todayXp,
        boolean goalReachedToday,
        int currentStreak,
        int bestStreak,
        Calendar calendar,
        Sources sources,
        List<RecentXp> recent,
        List<BadgeView> badges) {

    public ProgressOverviewResponse {
        recent = recent == null ? List.of() : List.copyOf(recent);
        badges = badges == null ? List.of() : List.copyOf(badges);
    }

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

        public Calendar {
            goalDays = goalDays == null ? List.of() : List.copyOf(goalDays);
        }
    }

    /** XP earned from Learn, from Practice, from reviews and daily goals, and earlier XP (e.g. old class rewards). */
    public record Sources(int learn, int practice, int reviewsAndGoals, int earlier) {
    }

    /** {@code kind}: LEARN_LESSON, PRACTICE_SET, QUIZ_STREAK, MISTAKES_REVIEW, DAILY_GOAL or EARLIER. */
    public record RecentXp(String kind, String title, int xp, LocalDateTime at) {
    }

    /** {@code key} is the {@code RewardBadge} name. */
    public record BadgeView(String key, boolean earned, LocalDateTime earnedAt) {
    }
}
