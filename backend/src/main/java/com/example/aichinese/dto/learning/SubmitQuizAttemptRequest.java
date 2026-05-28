package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body for
 * {@code POST /api/v1/students/quizzes/{quizId}/attempts} — the
 * student submits all of their answers in one batch and the service
 * scores the attempt synchronously.
 *
 * <p>The wrapping list is {@link NotEmpty} (at least one answer
 * must be supplied) and the service additionally requires
 * <em>every</em> quiz question to have a matching entry — partial
 * submissions are rejected with {@code 400 Bad Request} carrying
 * "Every quiz question requires an answer." (see
 * {@code StudentLearningService.submitQuizAttempt}). Duplicate
 * answers for the same question are also rejected. Clients that
 * want to model an "I gave up" interaction must therefore send an
 * explicit sentinel answer (a non-matching option id or a
 * blank-but-not-empty placeholder for free-text questions) rather
 * than omit the row.</p>
 *
 * <p>The response is a {@link QuizAttemptResultResponse} containing
 * the per-question correctness, the total score, and (on first
 * passing attempt) the XP awarded. Replays of an already-passed
 * quiz are still graded but earn zero new XP — see the idempotency
 * note on {@link QuizAttemptResultResponse}.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor defensively copies {@link #answers}
 * (with {@code null} → {@link List#of()} normalisation) so external
 * mutation of the caller's list cannot leak into the bound payload
 * after construction. Each child
 * {@link QuizSubmissionAnswerRequest} is independently validated
 * via the {@link Valid @Valid} cascade.</p>
 *
 * @param answers per-question answer entries; non-{@code null}, at
 *                least one entry, and ultimately one entry per quiz
 *                question (enforced server-side)
 */
@JsonPropertyOrder({ "answers" })
public record SubmitQuizAttemptRequest(
        @Size(max = 200, message = "A quiz attempt cannot contain more than 200 answers.")
        @Valid
        @NotEmpty(message = "At least one answer is required.")
        List<@NotNull(message = "Quiz answer must not be null.") QuizSubmissionAnswerRequest> answers) {

    /**
     * Defensive canonical constructor: defensively copies
     * {@link #answers} (with {@code null} → {@link List#of()}
     * normalisation) so external mutation cannot leak into the
     * bound payload. {@code null} input collapses to an empty list,
     * which {@link NotEmpty @NotEmpty} then rejects with the
     * standard validation error.
     */
    public SubmitQuizAttemptRequest {
        answers = immutableListOrEmpty(answers);
    }
}
