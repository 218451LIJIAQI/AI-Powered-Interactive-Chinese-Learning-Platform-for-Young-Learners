package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Student-facing view of a quiz option, surfaced inside
 * {@link StudentQuizQuestionResponse#options()}.
 *
 * <p>Deliberately <em>omits</em> the {@code correctOption} flag that
 * {@link QuizOptionRequest} and {@link TeacherQuizOptionResponse}
 * carry — graded quizzes are part of the assessment engine and must
 * not leak the answer before the student submits. This is the
 * pragmatic anti-cheat boundary for quizzes (contrast with the
 * platform-managed activity library, where the answer <em>is</em> on
 * the wire because XP is not gated on correctness — see the note on
 * {@code StudentStoryQuestionResponse#optionsPayload()}).</p>
 *
 * <p>{@link #displayOrder} preserves the teacher's canonical order;
 * the front-end may shuffle on each play to prevent positional
 * memorisation.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>All three fields are non-{@code null} — the underlying
 *       {@link com.example.aichinese.entity.QuizOption} schema
 *       declares {@code option_text} and {@code sort_order} as
 *       {@code NOT NULL}, and the row id is always populated for
 *       persisted records.</li>
 * </ul>
 *
 * @param id           {@link com.example.aichinese.entity.QuizOption}
 *                     identifier (used as the React list key and as
 *                     the answer payload when submitting); never
 *                     {@code null}
 * @param optionText   text rendered on the option pill; never
 *                     {@code null}
 * @param displayOrder 0-based teacher-canonical render order
 *                     (front-end may shuffle); never {@code null}
 */
@JsonPropertyOrder({ "id", "optionText", "displayOrder" })
public record QuizOptionResponse(
        Long id,
        String optionText,
        Integer displayOrder) {

    /**
     * Defensive canonical constructor: enforces non-null on all
     * three fields per the entity schema's {@code NOT NULL}
     * constraints.
     */
    public QuizOptionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(optionText, "optionText must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
