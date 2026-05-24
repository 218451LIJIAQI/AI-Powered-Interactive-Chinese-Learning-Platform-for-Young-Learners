package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

/**
 * One row of the teacher-facing quiz attempts list returned by
 * {@code GET /api/v1/teachers/quizzes/{quizId}/attempts}
 * (paginated). Drives the "Who has attempted my quiz" table on
 * the teacher quiz analytics screen.
 *
 * <p>Deliberately compact — the per-question breakdown for any
 * given attempt lives behind a separate detail endpoint
 * (the student's own {@code GET /quiz-attempts/{attemptId}}, which
 * teachers can also access for their owned quizzes). Keeping this
 * row to score + identity + timestamp makes the list cheap to
 * paginate over hundreds of attempts.</p>
 *
 * <p>{@link #studentName} is denormalised from
 * {@code users.display_name} so the teacher sees the student's
 * preferred display name without an extra join. {@link #score} /
 * {@link #maxScore} let the front-end compute and colour-code the
 * percentage; the {@link QuizAttemptResultResponse#passed()} flag
 * lives only on the per-attempt detail endpoint.</p>
 */
public record TeacherQuizAttemptSummaryResponse(
        Long attemptId,
        Long studentId,
        String studentName,
        Integer score,
        Integer maxScore,
        LocalDateTime submittedAt) {
}
