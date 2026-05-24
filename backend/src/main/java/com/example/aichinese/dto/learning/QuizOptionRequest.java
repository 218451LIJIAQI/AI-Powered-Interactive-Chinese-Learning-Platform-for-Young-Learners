package com.example.aichinese.dto.learning;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * One multiple-choice option attached to a quiz question.
 *
 * <p>Replayed as part of {@link CreateQuizQuestionRequest#options()}
 * or {@link UpdateQuizQuestionRequest#options()} — the parent
 * question owns the lifecycle, and the entire option list is
 * replaced atomically on each save.</p>
 *
 * <p>{@link #correctOption} is primitive {@code boolean} so the JSON
 * wire format always emits {@code true}/{@code false} without null
 * ambiguity. The teacher-side service currently rejects the question
 * unless exactly one option carries {@code correctOption == true}
 * (see {@code TeacherQuizService.applyQuizQuestion}); future
 * question types that allow multiple correct options would relax
 * the check there without changing this DTO's shape.</p>
 *
 * <p>Note that the student-facing counterpart
 * {@link QuizOptionResponse} <em>omits</em> {@link #correctOption}
 * to prevent the answer from leaking before the student submits.
 * Only the teacher-facing {@link TeacherQuizOptionResponse} surfaces
 * the correct flag.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * {@link #optionText} so Bean Validation evaluates the canonical
 * form (a paste with stray leading/trailing whitespace cannot
 * inflate the length past the cap or sneak past
 * {@link NotBlank @NotBlank}) and so the persisted option, the
 * audit log, and the
 * {@link CreateQuizQuestionRequest#correctAnswerText() denormalised
 * correct-answer text} all see the same trimmed value. {@code null}
 * input is preserved so {@code @NotBlank} fires correctly.</p>
 *
 * @param optionText   text rendered on the option pill (1–1000
 *                     chars after trim); required
 * @param correctOption {@code true} when this option is the
 *                     correct answer for the parent question
 * @param displayOrder 0-based render position in the option list;
 *                     defaults to {@code 0} server-side when
 *                     {@code null}
 */
@JsonPropertyOrder({ "optionText", "correctOption", "displayOrder" })
public record QuizOptionRequest(
        @NotBlank(message = "Option text is required.")
        @Size(max = 1000, message = "Option text must be 1000 characters or fewer.")
        String optionText,
        boolean correctOption,
        @PositiveOrZero(message = "Option display order must be greater than or equal to 0.")
        Integer displayOrder) {

    /**
     * Defensive canonical constructor: trims {@link #optionText} so
     * Bean Validation sees the canonical form (preventing length-cap
     * bypass via leading/trailing whitespace) and matches the
     * service's downstream
     * {@code contentSafetyService.requireSafeText} trimming.
     * {@code null} input is preserved so
     * {@link NotBlank @NotBlank} fires correctly.
     */
    public QuizOptionRequest {
        optionText = optionText == null ? null : optionText.trim();
    }
}
