package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Teacher-facing view of one quiz option, surfaced inside
 * {@link TeacherQuizQuestionResponse#options()}.
 *
 * <p>Mirrors {@link QuizOptionRequest} plus the database-assigned
 * {@link #id}. Crucially, surfaces {@link #correctOption} so the
 * authoring UI can render the green "correct" tick and let the
 * teacher verify the marked-correct option count is right
 * (currently the service requires exactly one correct option per
 * option-based question type).</p>
 *
 * <p>The student-facing counterpart {@link QuizOptionResponse}
 * omits {@link #correctOption} — see the anti-cheat note there.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>All four fields are non-{@code null} (primitive boolean
 *       for {@link #correctOption}) per the entity schema's
 *       {@code NOT NULL} constraints.</li>
 * </ul>
 *
 * @param id            {@link com.example.aichinese.entity.QuizOption}
 *                      identifier; never {@code null}
 * @param optionText    text rendered on the option pill; never
 *                      {@code null}
 * @param correctOption {@code true} when this option is the
 *                      correct answer
 * @param displayOrder  0-based authoring order; never {@code null}
 */
@JsonPropertyOrder({ "id", "optionText", "correctOption", "displayOrder" })
public record TeacherQuizOptionResponse(
        Long id,
        String optionText,
        boolean correctOption,
        Integer displayOrder) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display / ordering fields per the entity schema's
     * {@code NOT NULL} constraints.
     */
    public TeacherQuizOptionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(optionText, "optionText must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
