package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Result of {@code POST /api/v1/students/quizzes/{quizId}/attempts}
 * (synchronous grading), and read endpoint
 * {@code GET /api/v1/students/quiz-attempts/{attemptId}}.
 *
 * <p>Carries both the headline metrics ({@link #score},
 * {@link #maxScore}, {@link #totalQuestions},
 * {@link #correctCount}, {@link #wrongCount}, {@link #passed}) and
 * the per-question breakdown ({@link #answers}) so the student-side
 * "Quiz finished" screen can render a single payload.</p>
 *
 * <h2>{@link #passed}</h2>
 * <p>Computed server-side as
 * {@code (score / maxScore) &gt;= 60%} — see
 * {@code StudentLearningService.submitQuizAttempt}. The 60% bar is
 * intentionally lenient for child learners; it can be tightened
 * per-quiz in a future iteration without changing this DTO.
 * Primitive {@code boolean} so the JSON always emits a value.</p>
 *
 * <h2>XP idempotency</h2>
 * <p>{@link #earnedPoints} is non-zero only on the first attempt
 * that crosses the {@link #passed} threshold for this
 * (student, quiz) pair; replays earn {@code 0}. {@link #totalPoints}
 * is the student's running XP after this attempt and is updated on
 * every passing attempt. This mirrors the
 * {@code completePlatformActivity} idempotency rule documented on
 * {@code StudentActivityCompletionResponse}.</p>
 *
 * <h2>{@link #answers}</h2>
 * <p>One {@link QuizAttemptResultQuestionResponse} per submitted
 * answer, in the question's canonical {@code displayOrder}.
 * Unanswered questions are NOT in this list — see the partial-
 * submission policy on {@link SubmitQuizAttemptRequest}. The list
 * is defensively copied by the canonical constructor, so callers
 * may safely mutate the original input collection.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #attemptId}, {@link #quizId} and {@link #quizTitle}
 *       are non-{@code null} — they identify the row the front-end
 *       deep-links to.</li>
 *   <li>{@link #score} and {@link #maxScore} are non-{@code null}
 *       (boxed because the entity columns are wrapper-typed) and
 *       both non-negative.</li>
 *   <li>{@link #totalQuestions}, {@link #correctCount},
 *       {@link #wrongCount}, {@link #earnedPoints} and
 *       {@link #totalPoints} are non-negative; the canonical
 *       constructor enforces it.</li>
 *   <li>{@link #startedAt} is non-{@code null};
 *       {@link #submittedAt} is non-{@code null} for a graded
 *       attempt (the only state in which this DTO is returned).</li>
 *   <li>{@link #answers} is non-{@code null} (empty list when the
 *       student answered nothing) and defensively copied.</li>
 * </ul>
 *
 * @param attemptId      the persisted {@code QuizAttempt} row id;
 *                       never {@code null}
 * @param quizId         parent quiz id (echoed for client-side
 *                       reconciliation); never {@code null}
 * @param quizTitle      denormalised quiz title to avoid a
 *                       follow-up GET; never {@code null}
 * @param score          sum of points earned from correct answers;
 *                       never {@code null} and non-negative
 * @param maxScore       sum of points across all quiz questions;
 *                       never {@code null} and non-negative
 * @param totalQuestions total questions in the quiz (matches the
 *                       length of {@link #answers} when the student
 *                       answered everything); non-negative
 * @param correctCount   number of correct answers; non-negative
 * @param wrongCount     {@code totalQuestions - correctCount}
 *                       (includes skipped questions); non-negative
 * @param earnedPoints   XP awarded by THIS attempt; {@code 0} on
 *                       non-first passes; non-negative
 * @param totalPoints    running XP after applying
 *                       {@link #earnedPoints}; non-negative
 * @param passed         {@code true} when
 *                       {@code score &gt;= 60%} of
 *                       {@link #maxScore}
 * @param startedAt      when the attempt was opened (server clock,
 *                       Asia/Singapore zone); never {@code null}
 * @param submittedAt    when the answers were graded
 *                       (Asia/Singapore zone); never {@code null}
 *                       for a graded attempt
 * @param answers        per-question result rows; never
 *                       {@code null}; defensively copied
 */
@JsonPropertyOrder({
        "attemptId",
        "quizId",
        "quizTitle",
        "score",
        "maxScore",
        "totalQuestions",
        "correctCount",
        "wrongCount",
        "earnedPoints",
        "totalPoints",
        "passed",
        "startedAt",
        "submittedAt",
        "answers"
})
public record QuizAttemptResultResponse(
        Long attemptId,
        Long quizId,
        String quizTitle,
        Integer score,
        Integer maxScore,
        int totalQuestions,
        int correctCount,
        int wrongCount,
        int earnedPoints,
        int totalPoints,
        boolean passed,
        LocalDateTime startedAt,
        LocalDateTime submittedAt,
        List<QuizAttemptResultQuestionResponse> answers) {

    /**
     * Defensive canonical constructor: enforces non-null on
     * identity / display / score / timestamp fields, enforces the
     * non-negative invariant on every count field (a regression
     * that propagates a negative count would break the front-end's
     * "X of Y correct" indicator), and defensively copies
     * {@link #answers} (with {@code null} → {@link List#of()}
     * normalisation) so external mutation cannot leak into the
     * bound payload after construction.
     */
    public QuizAttemptResultResponse {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(quizId, "quizId must not be null");
        Objects.requireNonNull(quizTitle, "quizTitle must not be null");
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(maxScore, "maxScore must not be null");
        Objects.requireNonNull(startedAt, "startedAt must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
        if (score < 0) {
            throw new IllegalArgumentException("score must be non-negative, was " + score);
        }
        if (maxScore < 0) {
            throw new IllegalArgumentException("maxScore must be non-negative, was " + maxScore);
        }
        if (totalQuestions < 0) {
            throw new IllegalArgumentException("totalQuestions must be non-negative, was " + totalQuestions);
        }
        if (correctCount < 0) {
            throw new IllegalArgumentException("correctCount must be non-negative, was " + correctCount);
        }
        if (wrongCount < 0) {
            throw new IllegalArgumentException("wrongCount must be non-negative, was " + wrongCount);
        }
        if (earnedPoints < 0) {
            throw new IllegalArgumentException("earnedPoints must be non-negative, was " + earnedPoints);
        }
        if (totalPoints < 0) {
            throw new IllegalArgumentException("totalPoints must be non-negative, was " + totalPoints);
        }
        answers = answers == null ? List.of() : List.copyOf(answers);
    }
}
