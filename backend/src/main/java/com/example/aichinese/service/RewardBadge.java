package com.example.aichinese.service;

/**
 * The badges students can earn, in the order the Progress page shows them. This is the single source of truth for
 * the badge catalogue: {@code DataSeeder} seeds exactly these rows and {@code XpService} awards them from real
 * events, so every seeded badge can be earned and every awarded badge exists. Each description states the rule.
 */
public enum RewardBadge {

    FIRST_STEPS(
            "First Steps",
            "Finish your first lesson or practice set.",
            "first-steps"),
    STREAK_EXPLORER(
            "Streak Explorer",
            "Reach your daily goal 3 days in a row.",
            "streak-explorer"),
    XP_STARTER(
            "XP Starter",
            "Earn 50 XP.",
            "xp-starter"),
    QUIZ_STAR(
            "Quiz Star",
            "Score 80% or more on a quiz.",
            "quiz-star"),
    LESSON_FINISHER(
            "Lesson Finisher",
            "Finish 5 lessons or practice sets.",
            "lesson-finisher"),
    TEN_DAY_STREAK(
            "10-day Streak",
            "Reach your daily goal 10 days in a row.",
            "ten-day-streak"),
    UNIT_CHAMPION(
            "Unit Champion",
            "Finish every lesson of a Learn unit, review included.",
            "unit-champion"),
    XP_300(
            "300 XP",
            "Earn 300 XP and reach Level 4.",
            "xp-300");

    private final String badgeName;
    private final String description;
    private final String iconKey;

    RewardBadge(String badgeName, String description, String iconKey) {
        this.badgeName = badgeName;
        this.description = description;
        this.iconKey = iconKey;
    }

    public String badgeName() {
        return badgeName;
    }

    public String description() {
        return description;
    }

    public String iconKey() {
        return iconKey;
    }
}
