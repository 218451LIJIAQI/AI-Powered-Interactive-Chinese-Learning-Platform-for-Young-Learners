package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;

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
 * submission policy on {@link SubmitQuizAttemptRequest}.</p>
 *
 * @param attemptId        the persisted {@code QuizAttempt} row id
 * @param quizId           parent quiz id (echoed for client-side
 *                         reconciliation)
 * @param quizTitle        denormalised quiz title to avoid a
 *                         follow-up GET
 * @param score            sum of points earned from correct answers
 * @param maxScore         sum of points across all quiz questions
 * @param totalQuestions   total questions in the quiz (matches the
 *                         length of {@link #answers} when the student
 *                         answered everything)
 * @param correctCount     number of correct answers
 * @param wrongCount       {@code totalQuestions - correctCount}
 *                         (includes skipped questions)
 * @param earnedPoints     XP awarded by THIS attempt; {@code 0} on
 *                         non-first passes
 * @param totalPoints      running XP after applying
 *                         {@link #earnedPoints}
 * @param passed           {@code true} when {@code score &gt;= 60%}
 *                         of {@link #maxScore}
 * @param startedAt        when the attempt was opened (server clock)
 * @param submittedAt      when the answers were graded
 * @param answers          per-question result rows
 */
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
}
