package com.example.aichinese.dto.learning;

/**
 * Lightweight quiz row in the student-facing list endpoint
 * {@code GET /api/v1/students/quizzes} (paginated, optional
 * {@code lessonId} filter).
 *
 * <p>Drives the "Available quizzes" tab in the student UI; the
 * detail view ({@link StudentQuizResponse}) is fetched lazily when
 * the student opens a specific quiz. Surfacing only
 * {@link #questionCount} (instead of the full question array) keeps
 * the list payload small even for quizzes with dozens of
 * questions.</p>
 *
 * <p>Only quizzes whose parent lesson is published and accessible to
 * the student reach this endpoint. {@link #timeLimitMinutes} of
 * {@code 0} or {@code null} both mean "untimed" — see
 * {@link CreateQuizRequest}.</p>
 *
 * @param id                quiz row id
 * @param lessonId          parent lesson id; needed for breadcrumb
 *                          navigation back to the lesson
 * @param lessonTitle       denormalised lesson title to avoid a
 *                          follow-up GET
 * @param title             quiz title
 * @param description       optional quiz description
 * @param timeLimitMinutes  duration cap; {@code 0} or {@code null}
 *                          means untimed
 * @param totalPoints       sum of {@code points} across all
 *                          questions
 * @param questionCount     number of questions on the quiz
 */
public record StudentQuizSummaryResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        Integer timeLimitMinutes,
        Integer totalPoints,
        int questionCount) {
}
