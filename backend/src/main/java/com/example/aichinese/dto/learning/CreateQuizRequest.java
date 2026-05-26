package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/lessons/{lessonId}/quizzes} —
 * the teacher creates an empty quiz shell. Questions are added in
 * follow-up calls to {@code POST /quizzes/{quizId}/questions} (one
 * {@link CreateQuizQuestionRequest} per question) so the authoring
 * UI can save the header first and stream questions in afterwards
 * without losing partial work.
 *
 * <p>The newly created quiz is owned by the lesson's teacher and
 * starts in {@link com.example.aichinese.entity.enums.ContentStatus#DRAFT}
 * until the teacher publishes it via the lesson workflow. It is not
 * visible to students until then.</p>
 *
 * <h2>{@link #timeLimitMinutes}</h2>
 * <p>{@code 0} or {@code null} both mean "untimed" — the
 * student-side {@code StudentQuizResponse} surfaces the same value
 * verbatim and the front-end interprets it. {@link PositiveOrZero}
 * lets {@code 0} act as the explicit "no time limit" sentinel without
 * collapsing it to {@code null} (which clients sometimes send as
 * the missing-field value during partial saves).</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly strips surrounding Unicode whitespace from
 * {@link #title} and {@link #description} so Bean Validation
 * evaluates the canonical form (a paste with stray whitespace
 * cannot inflate the length past the cap or sneak past
 * {@link NotBlank @NotBlank}) and so the persisted entity, the
 * audit log, and the catalogue search index see the same stripped
 * value. {@link #description} collapses to {@code null} when
 * blank so the downstream "is this set?" check is a single
 * null-check.</p>
 *
 * @param title            short human-readable quiz title shown on
 *                         the lesson page (1–200 chars after stripping);
 *                         required
 * @param description      optional long-form description shown on
 *                         the quiz detail page (max 1000 chars after
 *                         stripping); {@code null} or blank collapses to
 *                         {@code null}
 * @param timeLimitMinutes optional countdown duration in minutes;
 *                         {@code null} or {@code 0} both mean
 *                         "untimed"
 */
@JsonPropertyOrder({ "title", "description", "timeLimitMinutes" })
public record CreateQuizRequest(
        @NotBlank(message = "Quiz title is required.")
        @Size(max = 200, message = "Quiz title must not exceed 200 characters.")
        String title,
        @Size(max = 1000, message = "Quiz description must not exceed 1000 characters.")
        String description,
        @PositiveOrZero(message = "Time limit must be greater than or equal to 0.")
        Integer timeLimitMinutes) {

    /**
     * Defensive canonical constructor: strips string fields so Bean
     * Validation sees the canonical form (preventing length-cap
     * bypass via leading/trailing whitespace) and collapses
     * {@link #description} to {@code null} when blank so the
     * downstream "is this set?" check is a single null-check.
     * {@code null} {@link #title} is preserved so
     * {@link NotBlank @NotBlank} fires correctly.
     */
    public CreateQuizRequest {
        title = strip(title);
        description = stripToNull(description);
    }
}
