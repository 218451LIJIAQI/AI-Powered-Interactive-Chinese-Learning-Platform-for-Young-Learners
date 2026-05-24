package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Pre-submission view of one quiz question, surfaced inside
 * {@link StudentQuizResponse#questions()}.
 *
 * <p>Deliberately omits both {@code correctAnswerText} and the
 * teacher's {@code explanation} — those fields are revealed only
 * <em>after</em> the student submits, on
 * {@link QuizAttemptResultQuestionResponse}. {@link #options} is a
 * list of {@link QuizOptionResponse} rows that themselves omit the
 * {@code correctOption} flag for the same anti-cheat reason. For
 * non-option-based question types ({@code FILL_BLANK},
 * {@code REARRANGE_SENTENCE}) the list will be empty and the
 * front-end renders the appropriate input control based on
 * {@link #questionType}.</p>
 *
 * <p>{@link #displayOrder} is the canonical authoring order; the
 * front-end may shuffle for replay value but the server never
 * relies on the order for grading.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #questionType}, {@link #questionText},
 *       {@link #points} and {@link #displayOrder} are non-{@code null}
 *       per the entity schema's {@code NOT NULL} constraints.</li>
 *   <li>{@link #options} is non-{@code null} (empty list for
 *       non-option-based question types) and defensively copied.</li>
 * </ul>
 *
 * @param id           {@link com.example.aichinese.entity.QuizQuestion}
 *                     identifier; never {@code null}
 * @param questionType enum-name string from
 *                     {@link com.example.aichinese.entity.enums.QuizQuestionType};
 *                     drives the front-end input control; never
 *                     {@code null}
 * @param questionText stem of the question; never {@code null}
 * @param points       maximum score the question contributes;
 *                     never {@code null}
 * @param displayOrder 0-based authoring order; never {@code null}
 * @param options      multiple-choice options for option-based
 *                     types (empty otherwise); never {@code null};
 *                     defensively copied
 */
@JsonPropertyOrder({ "id", "questionType", "questionText", "points", "displayOrder", "options" })
public record StudentQuizQuestionResponse(
        Long id,
        String questionType,
        String questionText,
        Integer points,
        Integer displayOrder,
        List<QuizOptionResponse> options) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / score / ordering fields per the entity
     * schema's {@code NOT NULL} constraints, and defensively copies
     * {@link #options} (with {@code null} → {@link List#of()}
     * normalisation) so external mutation cannot leak into the
     * bound payload after construction.
     */
    public StudentQuizQuestionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(questionType, "questionType must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(points, "points must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
        options = options == null ? List.of() : List.copyOf(options);
    }
}
