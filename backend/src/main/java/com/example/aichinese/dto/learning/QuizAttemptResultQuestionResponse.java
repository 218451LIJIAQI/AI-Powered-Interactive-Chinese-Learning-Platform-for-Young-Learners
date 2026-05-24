package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Per-question breakdown emitted inside
 * {@link QuizAttemptResultResponse#answers()}.
 *
 * <p>This is the <em>post-submission</em> view, so unlike
 * {@link StudentQuizQuestionResponse} (the pre-submission view) it
 * intentionally surfaces both the {@link #studentAnswer} and the
 * {@link #correctAnswer} side-by-side along with the teacher's
 * {@link #explanation}. The student needs the answer revealed once
 * grading is complete in order to learn from the attempt — that is
 * the entire purpose of the review screen.</p>
 *
 * <p>{@link #questionType} is the same enum string as on
 * {@link CreateQuizQuestionRequest} (a
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}
 * constant name). The front-end uses it to pick the correct
 * renderer for {@link #studentAnswer} / {@link #correctAnswer} —
 * for {@code REARRANGE_SENTENCE} this is a structured payload, for
 * option-based types it is the option label, for
 * {@code FILL_BLANK} it is plain text.</p>
 *
 * <p>{@link #correct} is the per-question outcome (primitive
 * {@code boolean}); {@link #points} mirrors the question's
 * configured maximum (the per-question scorer is binary, so an
 * incorrect answer simply doesn't add this value to the running
 * total — see {@link QuizAttemptResultResponse#score()}).
 * Unanswered questions do not appear in this list at all.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #questionId}, {@link #questionType},
 *       {@link #questionText}, {@link #studentAnswer} and
 *       {@link #points} are non-{@code null} — they correspond to
 *       columns the schema declares {@code NOT NULL} (or to the
 *       trimmed-non-blank student answer that the service has just
 *       persisted).</li>
 *   <li>{@link #correctAnswer} and {@link #explanation} may be
 *       {@code null} — both columns on
 *       {@link com.example.aichinese.entity.QuizQuestion} are
 *       nullable, and curators sometimes ship a question without
 *       prose explanation.</li>
 * </ul>
 *
 * @param questionId    {@link com.example.aichinese.entity.QuizQuestion}
 *                      identifier (used as the React list key);
 *                      never {@code null}
 * @param questionType  enum-name string from
 *                      {@link com.example.aichinese.entity.enums.QuizQuestionType};
 *                      drives the front-end renderer; never
 *                      {@code null}
 * @param questionText  stem of the question rendered atop the
 *                      review row; never {@code null}
 * @param studentAnswer trimmed answer the student submitted; never
 *                      {@code null}
 * @param correctAnswer canonical correct answer revealed once
 *                      graded; {@code null} when the question has
 *                      no model answer recorded
 * @param correct       {@code true} when {@link #studentAnswer}
 *                      matched {@link #correctAnswer} per the
 *                      type-specific scorer
 * @param points        the question's configured maximum score;
 *                      never {@code null}
 * @param explanation   teacher-authored rationale shown beneath the
 *                      review row; {@code null} when none is set
 */
@JsonPropertyOrder({
        "questionId",
        "questionType",
        "questionText",
        "studentAnswer",
        "correctAnswer",
        "correct",
        "points",
        "explanation"
})
public record QuizAttemptResultQuestionResponse(
        Long questionId,
        String questionType,
        String questionText,
        String studentAnswer,
        String correctAnswer,
        boolean correct,
        Integer points,
        String explanation) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / score fields the per-question review row
     * renders unconditionally per the entity schema's
     * {@code NOT NULL} constraints. {@link #correctAnswer} and
     * {@link #explanation} are intentionally permitted to be
     * {@code null} per the invariants documented above.
     */
    public QuizAttemptResultQuestionResponse {
        Objects.requireNonNull(questionId, "questionId must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(studentAnswer, "studentAnswer must not be null");
        Objects.requireNonNull(points, "points must not be null");
    }
}
