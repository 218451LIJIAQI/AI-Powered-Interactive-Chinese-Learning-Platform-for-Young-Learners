package com.example.aichinese.dto.learning;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/teachers/quiz-questions/{questionId}}
 * — the teacher edits one existing question.
 *
 * <p>Mirrors {@link CreateQuizQuestionRequest} field-for-field. When
 * the request includes {@link #options}, the service replaces the
 * entire option set in a single transaction (delete-and-reinsert
 * semantics) so the front-end can re-order, add or remove options
 * with one round-trip.</p>
 *
 * <p>See {@link CreateQuizQuestionRequest} for the full discussion
 * of {@link #questionType} branching, options-vs-text validation,
 * and field semantics.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * every string field, collapses optional blank-only strings to
 * {@code null}, and defensively copies {@link #options} — mirroring
 * the normalisation contract of {@link CreateQuizQuestionRequest}
 * so the persistence-layer code can use the same applier method
 * for both create and update paths.</p>
 *
 * @param questionType      enum-name string from
 *                          {@link com.example.aichinese.entity.enums.QuizQuestionType};
 *                          required, parsed server-side
 * @param questionText      stem of the question (max 2000 chars
 *                          after trim); required
 * @param correctAnswerText canonical correct answer for free-text /
 *                          structured types (max 1000 chars after
 *                          trim); blank collapses to {@code null}
 * @param points            non-negative score the question
 *                          contributes; required; must be
 *                          {@code &gt; 0}
 * @param displayOrder      0-based render position; non-negative;
 *                          defaults server-side to {@code 0} when
 *                          {@code null}
 * @param options           multiple-choice options for option-based
 *                          types (defensively copied; {@code null}
 *                          treated as empty); each entry
 *                          {@link Valid cascaded-validated}
 * @param explanation       teacher rationale (max 2000 chars after
 *                          trim); blank collapses to {@code null}
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
public record UpdateQuizQuestionRequest(
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
        @Valid
        List<QuizOptionRequest> options,
        @Size(max = 2000, message = "Explanation must be 2000 characters or fewer.")
        String explanation) {

    /**
     * Defensive canonical constructor: trims every string field so
     * Bean Validation sees the canonical form, collapses optional
     * blank-only strings to {@code null}, and defensively copies
     * {@link #options} (with {@code null} → {@link List#of()}
     * normalisation) so external mutation cannot leak into the
     * bound payload. {@code null} inputs on required fields are
     * preserved so {@link NotBlank @NotBlank} fires correctly.
     */
    public UpdateQuizQuestionRequest {
        questionType = trimOrNull(questionType);
        questionText = trimOrNull(questionText);
        correctAnswerText = trimToNullIfBlank(correctAnswerText);
        explanation = trimToNullIfBlank(explanation);
        options = options == null ? List.of() : List.copyOf(options);
    }

    private static String trimOrNull(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNullIfBlank(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
