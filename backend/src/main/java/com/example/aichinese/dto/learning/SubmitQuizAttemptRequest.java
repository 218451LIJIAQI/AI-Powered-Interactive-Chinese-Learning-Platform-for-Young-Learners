package com.example.aichinese.dto.learning;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

/**
 * Body for
 * {@code POST /api/v1/students/quizzes/{quizId}/attempts} — the
 * student submits all of their answers in one batch and the service
 * scores the attempt synchronously.
 *
 * <p>The wrapping list is {@link NotEmpty} (at least one answer must
 * be supplied) but the service tolerates partial submissions —
 * unanswered questions are silently treated as wrong answers worth
 * zero points. This lets the front-end ship the quiz when a student
 * runs out of time without forcing a placeholder answer per missing
 * question.</p>
 *
 * <p>The response is a {@link QuizAttemptResultResponse} containing
 * the per-question correctness, the total score, and (on first
 * passing attempt) the XP awarded. Replays of an already-passed
 * quiz are still graded but earn zero new XP — see the idempotency
 * note on {@link QuizAttemptResultResponse}.</p>
 */
public record SubmitQuizAttemptRequest(
        @Valid
        @NotEmpty(message = "At least one answer is required.")
        List<QuizSubmissionAnswerRequest> answers) {
}
