package com.example.aichinese.service;

/**
 * The badges the reward service awards. This is the single source of truth for the badge
 * catalogue: {@code DataSeeder} seeds exactly these rows and
 * {@link StudentLearningProgressService} awards them, so every seeded badge can be earned and
 * every awarded badge exists in the catalogue. Each description states the earning rule.
 */
public enum RewardBadge {

    FIRST_STEPS(
            "First Steps",
            "Earned the first time you submit a quiz or complete a practice activity.",
            "first-steps"),
    QUIZ_STAR(
            "Quiz Star",
            "Earned the first time you score at least 80% on a quiz.",
            "quiz-star"),
    LESSON_FINISHER(
            "Lesson Finisher",
            "Earned the first time you complete a lesson.",
            "lesson-finisher"),
    STREAK_EXPLORER(
            "Streak Explorer",
            "Earned when you learn on 3 days in a row (submit a quiz or complete a practice activity each day).",
            "streak-explorer"),
    POINT_STARTER(
            "Point Starter",
            "Earned when you collect at least 50 learning points.",
            "point-starter");

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
