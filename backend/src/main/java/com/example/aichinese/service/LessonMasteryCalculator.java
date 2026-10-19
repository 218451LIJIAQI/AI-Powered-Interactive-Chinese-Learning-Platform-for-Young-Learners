package com.example.aichinese.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import com.example.aichinese.entity.LessonProgress;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizAttempt;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LessonMastery;

/**
 * Derived per-lesson mastery indicator (D25, FR-PROG-04). Nothing is stored:
 * <ul>
 *   <li>NOT_STARTED - no lesson progress and no quiz attempt;</li>
 *   <li>IN_PROGRESS - started (progress row, content viewed or a quiz attempted) but not completed;</li>
 *   <li>COMPLETED - lesson completed (all published quizzes submitted, or content + recorded videos
 *       viewed for a lesson without quizzes);</li>
 *   <li>MASTERED - lesson completed and the best quiz percentage is at least {@value #MASTERY_PERCENT} %.</li>
 * </ul>
 * The best quiz percentage of a lesson is the average, over its published quizzes the student
 * attempted, of each quiz's best submitted attempt (one quiz: simply the best attempt).
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

    public static LessonMastery mastery(LessonProgress progress, Integer bestQuizPercentage) {
        boolean started = progress != null;
        boolean completed = progress != null && progress.isCompleted();
        return mastery(started, completed, bestQuizPercentage);
    }

    /** Percentage of one submitted attempt, or null when it cannot be scored. */
    public static Integer attemptPercentage(QuizAttempt attempt) {
        if (attempt == null || !attempt.isSubmitted() || attempt.getScore() == null
                || attempt.getMaxScore() == null || attempt.getMaxScore() <= 0) {
            return null;
        }
        return QuizScoring.percentage(attempt.getScore(), attempt.getMaxScore());
    }

    /**
     * Best quiz percentage per lesson id for the given attempts (one student). Only attempts on
     * PUBLISHED quizzes count, so archived quizzes never affect mastery.
     */
    public static Map<Long, Integer> bestQuizPercentageByLesson(Collection<QuizAttempt> attempts) {
        Map<Long, Map<Long, Integer>> bestByLessonAndQuiz = new HashMap<>();
        if (attempts != null) {
            for (QuizAttempt attempt : attempts) {
                Integer percentage = attemptPercentage(attempt);
                Quiz quiz = attempt == null ? null : attempt.getQuiz();
                if (percentage == null || quiz == null || quiz.getLesson() == null
                        || quiz.getContentStatus() != ContentStatus.PUBLISHED) {
                    continue;
                }
                bestByLessonAndQuiz
                        .computeIfAbsent(quiz.getLesson().getId(), ignored -> new HashMap<>())
                        .merge(quiz.getId(), percentage, Math::max);
            }
        }
        return bestByLessonAndQuiz.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> average(entry.getValue().values())));
    }

    /** Rounded down for the same reason as {@link QuizScoring#percentage}; every lesson entry has at least one quiz. */
    private static int average(Collection<Integer> values) {
        return (int) Math.floor(values.stream().mapToInt(Integer::intValue).average().orElse(0));
    }
}
