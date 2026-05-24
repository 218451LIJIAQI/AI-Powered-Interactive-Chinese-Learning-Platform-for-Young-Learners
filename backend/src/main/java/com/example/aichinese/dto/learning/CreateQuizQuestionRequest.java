package com.example.aichinese.dto.learning;

import java.util.List;

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
 *       {@code CHARACTER_MEANING}) require at least one option with
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
 */
public record CreateQuizQuestionRequest(
        @NotBlank(message = "Question type is required.")
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
        @Valid
        List<QuizOptionRequest> options,
        @Size(max = 2000, message = "Explanation must be 2000 characters or fewer.")
        String explanation) {
}
