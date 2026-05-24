package com.example.aichinese.dto.learning;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * (time-limit zero-vs-null sentinel, normalisation, etc.) is
 * identical to {@link CreateQuizRequest}.</p>
 *
 * @param title            short quiz title (1–200 chars after
 *                         trim); required
 * @param description      optional long-form description (max 1000
 *                         chars after trim); blank collapses to
 *                         {@code null}
 * @param timeLimitMinutes countdown duration in minutes;
 *                         {@code null} or {@code 0} both mean
 *                         "untimed"
 */
@JsonPropertyOrder({ "title", "description", "timeLimitMinutes" })
public record UpdateQuizRequest(
        @NotBlank(message = "Quiz title is required.")
        @Size(max = 200, message = "Quiz title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Quiz description must not exceed 1000 characters.")
        String description,
        @PositiveOrZero(message = "Time limit must be greater than or equal to 0.")
        Integer timeLimitMinutes) {

    /**
     * Defensive canonical constructor: trims string fields so Bean
     * Validation sees the canonical form and collapses
     * {@link #description} to {@code null} when blank. {@code null}
     * {@link #title} is preserved so {@link NotBlank @NotBlank}
     * fires correctly.
     */
    public UpdateQuizRequest {
        title = title == null ? null : title.trim();
        if (description != null) {
            String trimmed = description.trim();
            description = trimmed.isEmpty() ? null : trimmed;
        }
    }
}
