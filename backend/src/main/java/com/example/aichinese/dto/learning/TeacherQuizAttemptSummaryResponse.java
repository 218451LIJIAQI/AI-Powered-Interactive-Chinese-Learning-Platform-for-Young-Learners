package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #attemptId}, {@link #studentId} and
 *       {@link #studentName} are non-{@code null} — the teacher
 *       attempts table renders them unconditionally.</li>
 *   <li>{@link #score} and {@link #maxScore} may be {@code null}
 *       for an attempt that was opened but never submitted (the
 *       grading pass populates both).</li>
 *   <li>{@link #submittedAt} is non-{@code null} for graded
 *       attempts (the only state this list endpoint surfaces).</li>
 * </ul>
 *
 * @param attemptId   {@code QuizAttempt} identifier; never
 *                    {@code null}
 * @param studentId   {@code StudentProfile} identifier; never
 *                    {@code null}
 * @param studentName denormalised display name; never {@code null}
 * @param score       sum of points earned; {@code null} for
 *                    ungraded attempts
 * @param maxScore    sum of points available; {@code null} for
 *                    ungraded attempts
 * @param submittedAt grading instant (Asia/Singapore zone); never
 *                    {@code null} for the attempts surfaced here
 */
@JsonPropertyOrder({ "attemptId", "studentId", "studentName", "score", "maxScore", "submittedAt" })
public record TeacherQuizAttemptSummaryResponse(
        Long attemptId,
        Long studentId,
        String studentName,
        Integer score,
        Integer maxScore,
        LocalDateTime submittedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / timestamp fields the teacher attempts
     * table renders unconditionally. {@link #score} and
     * {@link #maxScore} are intentionally permitted to be
     * {@code null} per the invariants documented above.
     */
    public TeacherQuizAttemptSummaryResponse {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(studentName, "studentName must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }
}
