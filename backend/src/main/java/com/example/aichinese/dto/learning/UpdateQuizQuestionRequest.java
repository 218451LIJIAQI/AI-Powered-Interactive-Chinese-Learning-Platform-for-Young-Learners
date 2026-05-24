package com.example.aichinese.dto.learning;

import java.util.List;

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
 */
public record UpdateQuizQuestionRequest(
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
