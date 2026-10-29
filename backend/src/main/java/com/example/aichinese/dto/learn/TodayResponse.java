package com.example.aichinese.dto.learn;

import java.time.LocalDate;
import java.util.List;

/**
 * Today at a glance (Learn and Practice rails): XP against the daily goal, the streak, the last seven days (oldest
 * first, today last), sets finished today and mistakes waiting.
 */
public record TodayResponse(
        LocalDate date,
        int dailyGoalXp,
        int todayXp,
        boolean goalReached,
        int currentStreak,
        List<Day> week,
        long setsDoneToday,
        long mistakesWaiting) {

    public TodayResponse {
        week = week == null ? List.of() : List.copyOf(week);
    }

    public record Day(LocalDate date, boolean goalReached) {
    }
}
