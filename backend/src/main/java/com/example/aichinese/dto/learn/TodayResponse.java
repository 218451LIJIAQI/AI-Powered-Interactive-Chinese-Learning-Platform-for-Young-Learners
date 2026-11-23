package com.example.aichinese.dto.learn;

import java.time.LocalDate;
import java.util.List;

/**
 * Today at a glance: XP against the daily goal, the last seven days (oldest first, today last) and mistakes waiting.
 */
public record TodayResponse(
        int dailyGoalXp,
        int todayXp,
        boolean goalReached,
        List<Day> week,
        long mistakesWaiting) {

    public record Day(LocalDate date, boolean goalReached) {
    }
}
