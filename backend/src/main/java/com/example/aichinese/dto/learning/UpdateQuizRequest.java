package com.example.aichinese.dto.learning;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/teachers/quizzes/{quizId}} — the
 * teacher edits the quiz header (title, description, time limit).
 * Question-level edits use the dedicated question endpoints and
 * are not represented here.
 *
 * <p>Mirrors {@link CreateQuizRequest} field-for-field; the
 * separation keeps the request shape predictable and lets the
 * authoring UI bind the same form component to either the
 * "Create new quiz" or the "Edit quiz" dialog. Field rationale
 * (time-limit zero-vs-null sentinel, etc.) is identical to
 * {@link CreateQuizRequest}.</p>
 */
public record UpdateQuizRequest(
        @NotBlank(message = "Quiz title is required.")
        @Size(max = 200, message = "Quiz title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Quiz description must not exceed 1000 characters.")
        String description,
        @PositiveOrZero(message = "Time limit must be greater than or equal to 0.")
        Integer timeLimitMinutes) {
}
