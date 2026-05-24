package com.example.aichinese.dto.learning;

import java.util.List;

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
 *       has any progress against (started AND finished both count).</li>
 *   <li>{@link #completedLessons} — lessons with
 *       {@code completionPercent >= 100}.</li>
 *   <li>{@link #quizAverage} — rounded average of every submitted
 *       quiz attempt's score percentage; {@code 0} when no
 *       attempts.</li>
 *   <li>{@link #overallCompletionPercent} — average of per-lesson
 *       {@code completionPercent} across all started lessons.</li>
 * </ul>
 *
 * <h2>Surfacing fields</h2>
 * <ul>
 *   <li>{@link #weakAreas} — distinct {@code feedbackType} values
 *       across the student's teacher feedback (server-capped at a
 *       small number; mirrors the cap on
 *       {@code TeacherStudentSummaryResponse.weakAreas}). Drives
 *       the "Focus on" panel.</li>
 *   <li>{@link #lastActivitySummary} — pre-formatted human string
 *       like "Submitted Lesson 3 quiz 2 hours ago"; empty when no
 *       activity yet. Always typed as {@code String} (not a
 *       structured object) because the service applies locale-aware
 *       formatting before emitting.</li>
 *   <li>{@link #recentLessons} — top-N most recently accessed
 *       lessons; see {@link StudentProgressLessonResponse}.</li>
 * </ul>
 */
public record StudentProgressSummaryResponse(
        int totalLessonsStarted,
        int completedLessons,
        int quizAverage,
        int overallCompletionPercent,
        List<String> weakAreas,
        String lastActivitySummary,
        List<StudentProgressLessonResponse> recentLessons) {
}
