package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/quizzes/{quizId}/questions}
 * — the teacher appends one question to an existing quiz.
 *
 * <p>Aggregate-root payload: a single request encapsulates both the
 * question and its full set of {@link QuizOptionRequest} child rows.
 * The {@link Valid} on {@link #options} cascades validation into
 * each option so the entire question (header + options) is rejected
 * atomically when any child fails.</p>
 *
 * <h2>{@link #questionType}</h2>
 * <p>Validated server-side via
 * {@code QuizQuestionType.fromValue} which throws a 400 with a
 * canonical "Allowed values are MULTIPLE_CHOICE, TRUE_FALSE, …"
 * message when the value does not match a known
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}
 * constant. The supported types determine whether
 * {@link #options} or {@link #correctAnswerText} is required:</p>
 * <ul>
 *   <li>Option-based types ({@code MULTIPLE_CHOICE},
 *       {@code TRUE_FALSE}, {@code MATCH_*}, {@code LISTEN_CHOOSE},
 *       {@code CHOOSE_PINYIN}, {@code CHOOSE_TONE},
 *       {@code CHARACTER_MEANING}) require exactly one option with
 *       {@link QuizOptionRequest#correctOption()} {@code == true}.</li>
 *   <li>Free-text type ({@code FILL_BLANK}) requires
 *       {@link #correctAnswerText} to be set; options are ignored.</li>
 *   <li>Structured type ({@code REARRANGE_SENTENCE}) uses
 *       {@link #correctAnswerText} as the canonical ordering payload.</li>
 * </ul>
 *
 * <p>{@link #points} contributes to the quiz's {@code totalPoints}
 * and the per-question {@code score} surfaced on
 * {@link QuizAttemptResultResponse}.</p>
 *
 * <p>{@link #explanation} is shown on
 * {@link QuizAttemptResultQuestionResponse#explanation()} and
 * {@link WrongAnswerResponse#explanation()} so students see the
 * teacher's rationale during review.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly strips surrounding Unicode
 * whitespace from every string field so Bean Validation evaluates the canonical
 * form (a paste with stray leading/trailing whitespace cannot
 * inflate the length past the cap or sneak past
 * {@code QuizQuestionType.fromValue}) and so every downstream
 * consumer sees the same stripped value. Optional fields
 * ({@link #correctAnswerText}, {@link #explanation}) collapse to
 * {@code null} when blank so the service's "free-text required"
 * branch is a single null-check. The {@link #options} list is
 * defensively copied (with {@code null} → empty list) so external
 * mutation of the caller's list cannot leak into the bound payload
 * after construction.</p>
 *
 * @param questionType      enum-name string from
 *                          {@link com.example.aichinese.entity.enums.QuizQuestionType}
 *                          (e.g. {@code MULTIPLE_CHOICE}); required;
 *                          parsed server-side
 * @param questionText      stem of the question rendered on the
 *                          quiz player (max 2000 chars after stripping);
 *                          required
 * @param correctAnswerText canonical correct answer for free-text /
 *                          structured types (max 1000 chars after
 *                          stripping); ignored for option-based types;
 *                          {@code null} or blank collapses to
 *                          {@code null}
 * @param points            non-negative score the question
 *                          contributes to the quiz total; required;
 *                          must be {@code &gt; 0}
 * @param displayOrder      0-based render position in the question
 *                          list; defaults to {@code 0} server-side
 *                          when {@code null}
 * @param options           multiple-choice options for option-based
 *                          types (defensively copied; {@code null}
 *                          treated as empty); each entry is
 *                          {@link Valid cascaded-validated}
 * @param explanation       optional teacher rationale shown on the
 *                          attempt-review screen (max 2000 chars
 *                          after stripping); {@code null} or blank
 *                          collapses to {@code null}
 */
@JsonPropertyOrder({
        "questionType",
        "questionText",
        "correctAnswerText",
        "points",
        "displayOrder",
        "options",
        "explanation"
})
public record CreateQuizQuestionRequest(
        @NotBlank(message = "Question type is required.")
        @Size(max = 50, message = "Question type must be 50 characters or fewer.")
        String questionType,
        @NotBlank(message = "Question text is required.")
        @Size(max = 2000, message = "Question text must be 2000 characters or fewer.")
        String questionText,
        @Size(max = 1000, message = "Correct answer text must be 1000 characters or fewer.")
        String correctAnswerText,
        @NotNull(message = "Question points are required.")
        @Positive(message = "Question points must be greater than 0.")
        Integer points,
        @PositiveOrZero(message = "Question display order must be greater than or equal to 0.")
        Integer displayOrder,
        @Size(max = 200, message = "A quiz question cannot contain more than 200 options.")
        @Valid
        List<@NotNull(message = "Quiz option must not be null.") QuizOptionRequest> options,
        @Size(max = 2000, message = "Explanation must be 2000 characters or fewer.")
        String explanation) {

    /**
     * Defensive canonical constructor: strips every string field so
     * Bean Validation sees the canonical form, collapses optional
     * blank-only strings to {@code null} so the service's
     * free-text-required branch is a single null-check, and
     * defensively copies {@link #options} (with {@code null} →
     * {@link List#of()} normalisation) so external mutation cannot
     * leak into the bound payload. {@code null} inputs on required
     * fields are preserved so {@link NotBlank @NotBlank} fires
     * correctly.
     */
    public CreateQuizQuestionRequest {
        questionType = strip(questionType);
        questionText = strip(questionText);
        correctAnswerText = stripToNull(correctAnswerText);
        explanation = stripToNull(explanation);
        options = immutableListOrEmpty(options);
    }
}
