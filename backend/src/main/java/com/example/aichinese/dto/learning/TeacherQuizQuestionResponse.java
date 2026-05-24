package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Teacher-facing view of one quiz question, surfaced inside
 * {@link TeacherQuizResponse#questions()}.
 *
 * <p>Mirrors {@link CreateQuizQuestionRequest} field-for-field plus
 * the database-assigned {@link #id}. Critically, this DTO surfaces
 * {@link #correctAnswerText} and {@link #explanation} — both fields
 * are stripped from the student-facing
 * {@link StudentQuizQuestionResponse} until the student submits an
 * attempt and receives the post-submission
 * {@link QuizAttemptResultQuestionResponse} review payload.</p>
 *
 * <p>{@link #options} carries {@link TeacherQuizOptionResponse}
 * rows which expose the per-option {@code correctOption} flag (the
 * student-facing {@link QuizOptionResponse} omits it). For
 * non-option-based question types
 * ({@link com.example.aichinese.entity.enums.QuizQuestionType#FILL_BLANK},
 * {@link com.example.aichinese.entity.enums.QuizQuestionType#REARRANGE_SENTENCE})
 * the list is empty.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #questionType}, {@link #questionText},
 *       {@link #points} and {@link #displayOrder} are non-{@code null}
 *       per the entity schema's {@code NOT NULL} constraints.</li>
 *   <li>{@link #correctAnswerText} and {@link #explanation} may be
 *       {@code null} per the entity columns' nullable definition.</li>
 *   <li>{@link #options} is non-{@code null} (empty list for
 *       non-option-based question types) and defensively copied.</li>
 * </ul>
 *
 * @param id                {@link com.example.aichinese.entity.QuizQuestion}
 *                          identifier; never {@code null}
 * @param questionType      enum-name string; never {@code null}
 * @param questionText      stem of the question; never {@code null}
 * @param correctAnswerText canonical correct answer (free-text /
 *                          structured types); may be {@code null}
 *                          for option-based types
 * @param points            maximum score; never {@code null}
 * @param displayOrder      0-based authoring order; never
 *                          {@code null}
 * @param explanation       teacher rationale; may be {@code null}
 * @param options           teacher-facing option rows (defensively
 *                          copied); never {@code null}
 */
@JsonPropertyOrder({
        "id",
        "questionType",
        "questionText",
        "correctAnswerText",
        "points",
        "displayOrder",
        "explanation",
        "options"
})
public record TeacherQuizQuestionResponse(
        Long id,
        String questionType,
        String questionText,
        String correctAnswerText,
        Integer points,
        Integer displayOrder,
        String explanation,
        List<TeacherQuizOptionResponse> options) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / score / ordering fields per the entity
     * schema's {@code NOT NULL} constraints, and defensively copies
     * {@link #options} (with {@code null} → {@link List#of()}
     * normalisation) so external mutation cannot leak into the
     * bound payload after construction.
     */
    public TeacherQuizQuestionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(points, "points must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
        options = options == null ? List.of() : List.copyOf(options);
    }
}
