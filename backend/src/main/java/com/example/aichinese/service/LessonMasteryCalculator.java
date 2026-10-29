package com.example.aichinese.service;

import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.enums.LessonMastery;

/**
 * Derived per-lesson mastery indicator (FRPROG02). Nothing is stored:
 * <ul>
 *   <li>NOT_STARTED - no video watched, no activity done and no quiz attempted;</li>
 *   <li>IN_PROGRESS - started but not completed;</li>
 *   <li>COMPLETED - every live activity and quiz of the lesson is done (the video is not a step);</li>
 *   <li>MASTERED - lesson completed and the best quiz percentage is at least {@value #MASTERY_PERCENT} %.</li>
 * </ul>
 * The best quiz percentage of a lesson is the average, over its live quizzes the student attempted, of each quiz's
 * best submitted attempt (one quiz: simply the best attempt).
 */
public final class LessonMasteryCalculator {

    public static final int MASTERY_PERCENT = 80;

    private LessonMasteryCalculator() {
    }

    public static LessonMastery mastery(boolean started, boolean completed, Integer bestQuizPercentage) {
        if (completed) {
            return bestQuizPercentage != null && bestQuizPercentage >= MASTERY_PERCENT
                    ? LessonMastery.MASTERED
                    : LessonMastery.COMPLETED;
        }
        return started || bestQuizPercentage != null ? LessonMastery.IN_PROGRESS : LessonMastery.NOT_STARTED;
    }

    /** Percentage of one submitted attempt, or null when it cannot be scored. */
    public static Integer attemptPercentage(QuizAttempt attempt) {
        if (attempt == null || !attempt.isSubmitted() || attempt.getScore() == null
                || attempt.getMaxScore() == null || attempt.getMaxScore() <= 0) {
            return null;
        }
        return QuizScoring.percentage(attempt.getScore(), attempt.getMaxScore());
    }
}
