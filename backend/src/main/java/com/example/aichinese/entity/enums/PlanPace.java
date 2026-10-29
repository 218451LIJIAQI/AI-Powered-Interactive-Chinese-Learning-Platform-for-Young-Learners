package com.example.aichinese.entity.enums;

/**
 * How much time a student can give (setup step 3). The pace sets the daily goal and, with the lesson lengths, how
 * many weeks each step of the plan takes.
 */
public enum PlanPace {

    LIGHT(10, 3, 10),

    REGULAR(15, 5, 20),

    SERIOUS(20, 7, 30),

    INTENSE(30, 7, 30);

    private final int minutesPerDay;
    private final int daysPerWeek;
    private final int dailyGoalXp;

    PlanPace(int minutesPerDay, int daysPerWeek, int dailyGoalXp) {
        this.minutesPerDay = minutesPerDay;
        this.daysPerWeek = daysPerWeek;
        this.dailyGoalXp = dailyGoalXp;
    }

    public int minutesPerDay() {
        return minutesPerDay;
    }

    public int daysPerWeek() {
        return daysPerWeek;
    }

    public int minutesPerWeek() {
        return minutesPerDay * daysPerWeek;
    }

    public int dailyGoalXp() {
        return dailyGoalXp;
    }
}
