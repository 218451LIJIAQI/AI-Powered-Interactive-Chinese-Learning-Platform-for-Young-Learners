package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One mistake-review row returned by:
 * <ul>
 *   <li>{@code GET /api/v1/students/wrong-answers} — paginated
 *       cross-quiz feed of every wrong answer the student has ever
 *       submitted, newest first. Drives the "Mistakes to review"
 *       widget on the student dashboard.</li>
 *   <li>{@code GET /api/v1/students/quiz-attempts/{attemptId}/wrong-answers}
 *       — non-paginated subset filtered to a single attempt.
 *       Drives the per-attempt review screen.</li>
 * </ul>
 *
 * <p>Carries everything the front-end needs to render a flashcard-
 * style review card: the question text, what the student answered,
 * what the right answer was, and the teacher's explanation. The
 * fields mirror the post-submission view
 * {@link QuizAttemptResultQuestionResponse}, with the parent
 * {@link #quizTitle} denormalised in so the cross-quiz feed can
 * label each card with its source.</p>
 *
 * <p>{@link #questionType} is the same enum string as on
 * {@link CreateQuizQuestionRequest} — see
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #attemptId}, {@link #questionId}, {@link #quizTitle},
 *       {@link #questionText}, {@link #questionType},
 *       {@link #studentAnswer} and {@link #submittedAt} are
 *       non-{@code null} — they correspond to columns the schema
 *       declares {@code NOT NULL} (or to the trimmed-non-blank
 *       student answer that the service persisted).</li>
 *   <li>{@link #correctAnswer} and {@link #explanation} may be
 *       {@code null} when the curator shipped a question without
 *       a model answer or rationale.</li>
 * </ul>
 *
 * @param attemptId     parent {@code QuizAttempt} identifier; never
 *                      {@code null}
 * @param questionId    {@code QuizQuestion} identifier; never
 *                      {@code null}
 * @param quizTitle     denormalised quiz title for the
 *                      cross-quiz feed; never {@code null}
 * @param questionText  question stem rendered on the review card;
 *                      never {@code null}
 * @param questionType  enum-name string from
 *                      {@link com.example.aichinese.entity.enums.QuizQuestionType};
 *                      drives the front-end renderer; never
 *                      {@code null}
 * @param studentAnswer trimmed answer the student submitted; never
 *                      {@code null}
 * @param correctAnswer canonical correct answer; may be
 *                      {@code null} when the question has no model
 *                      answer recorded
 * @param explanation   teacher rationale; may be {@code null} when
 *                      none is set
 * @param submittedAt   grading instant (Asia/Singapore zone); never
 *                      {@code null}
 */
@JsonPropertyOrder({
        "attemptId",
        "questionId",
        "quizTitle",
        "questionText",
        "questionType",
        "studentAnswer",
        "correctAnswer",
        "explanation",
        "submittedAt"
})
public record WrongAnswerResponse(
        Long attemptId,
        Long questionId,
        String quizTitle,
        String questionText,
        String questionType,
        String studentAnswer,
        String correctAnswer,
        String explanation,
        LocalDateTime submittedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / answer / timestamp fields per the
     * entity schema's {@code NOT NULL} constraints.
     * {@link #correctAnswer} and {@link #explanation} are
     * intentionally permitted to be {@code null} per the invariants
     * documented above.
     */
    public WrongAnswerResponse {
        Objects.requireNonNull(attemptId, "attemptId must not be null");
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(quizTitle, "quizTitle must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(studentAnswer, "studentAnswer must not be null");
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }
}
