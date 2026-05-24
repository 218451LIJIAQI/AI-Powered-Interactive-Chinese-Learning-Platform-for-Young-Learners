package com.example.aichinese.dto.learning;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Roll-up of the student's overall learning progress, returned by
 * {@code GET /api/v1/students/progress}.
 *
 * <p>Drives the headline stats on the student dashboard. Composed
 * server-side by {@code StudentLearningService.getProgressSummary}
 * by aggregating across the student's
 * {@link com.example.aichinese.entity.LessonProgress},
 * {@link com.example.aichinese.entity.QuizAttempt}, and
 * {@link com.example.aichinese.entity.TeacherFeedback} rows.</p>
 *
 * <h2>Headline metrics</h2>
 * <ul>
 *   <li>{@link #totalLessonsStarted} — distinct lessons the student
 *       has any progress against (started AND finished both count);
 *       non-negative.</li>
 *   <li>{@link #completedLessons} — lessons with
 *       {@code completionPercent >= 100}; non-negative.</li>
 *   <li>{@link #quizAverage} — rounded average of every submitted
 *       quiz attempt's score percentage; {@code 0} when no
 *       attempts; clamped to {@code [0, 100]}.</li>
 *   <li>{@link #overallCompletionPercent} — average of per-lesson
 *       {@code completionPercent} across all started lessons;
 *       clamped to {@code [0, 100]}.</li>
 * </ul>
 *
 * <h2>Surfacing fields</h2>
 * <ul>
 *   <li>{@link #weakAreas} — distinct {@code feedbackType} values
 *       across the student's teacher feedback (server-capped at a
 *       small number; mirrors the cap on
 *       {@code TeacherStudentSummaryResponse.weakAreas}). Drives
 *       the "Focus on" panel. Defensively copied by the canonical
 *       constructor.</li>
 *   <li>{@link #lastActivitySummary} — pre-formatted human string
 *       like "Submitted Lesson 3 quiz 2 hours ago"; empty string
 *       when no activity yet (the canonical constructor coerces
 *       {@code null} to empty). Always typed as {@code String}
 *       (not a structured object) because the service applies
 *       locale-aware formatting before emitting.</li>
 *   <li>{@link #recentLessons} — top-N most recently accessed
 *       lessons; see {@link StudentProgressLessonResponse}.
 *       Defensively copied by the canonical constructor.</li>
 * </ul>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>All four counter fields are non-negative; the two percent
 *       fields are additionally clamped to {@code [0, 100]}.</li>
 *   <li>{@link #weakAreas}, {@link #lastActivitySummary} and
 *       {@link #recentLessons} are non-{@code null}.</li>
 * </ul>
 *
 * @param totalLessonsStarted      count of distinct lessons with
 *                                 any progress; non-negative
 * @param completedLessons         count of lessons with
 *                                 {@code completionPercent &gt;= 100};
 *                                 non-negative
 * @param quizAverage              rounded quiz score-percentage
 *                                 average; in {@code [0, 100]}
 * @param overallCompletionPercent average lesson completion across
 *                                 started lessons; in
 *                                 {@code [0, 100]}
 * @param weakAreas                short list of weak-area labels
 *                                 (defensively copied); never
 *                                 {@code null}
 * @param lastActivitySummary      pre-formatted "last activity"
 *                                 sentence; empty string when no
 *                                 activity; never {@code null}
 * @param recentLessons            recently-accessed lessons
 *                                 (defensively copied); never
 *                                 {@code null}
 */
@JsonPropertyOrder({
        "totalLessonsStarted",
        "completedLessons",
        "quizAverage",
        "overallCompletionPercent",
        "weakAreas",
        "lastActivitySummary",
        "recentLessons"
})
public record StudentProgressSummaryResponse(
        int totalLessonsStarted,
        int completedLessons,
        int quizAverage,
        int overallCompletionPercent,
        List<String> weakAreas,
        String lastActivitySummary,
        List<StudentProgressLessonResponse> recentLessons) {

    /**
     * Defensive canonical constructor: enforces the non-negative
     * invariants on the counter fields, clamps the percent fields
     * to {@code [0, 100]} (a regression in the roll-up formula that
     * emitted {@code 101} would skew the dashboard progress widget),
     * defensively copies the list fields (with {@code null} →
     * {@link List#of()} normalisation), and coerces a {@code null}
     * {@link #lastActivitySummary} to the empty string so the
     * front-end's documented "empty when no activity" contract is
     * upheld without a redundant null-check.
     */
    public StudentProgressSummaryResponse {
        if (totalLessonsStarted < 0) {
            throw new IllegalArgumentException("totalLessonsStarted must be non-negative, was " + totalLessonsStarted);
        }
        if (completedLessons < 0) {
            throw new IllegalArgumentException("completedLessons must be non-negative, was " + completedLessons);
        }
        quizAverage = Math.max(0, Math.min(100, quizAverage));
        overallCompletionPercent = Math.max(0, Math.min(100, overallCompletionPercent));
        weakAreas = weakAreas == null ? List.of() : List.copyOf(weakAreas);
        lastActivitySummary = lastActivitySummary == null ? "" : lastActivitySummary;
        recentLessons = recentLessons == null ? List.of() : List.copyOf(recentLessons);
    }
}
