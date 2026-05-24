package com.example.aichinese.dto.student.activity;

import java.util.Objects;

/**
 * One comprehension prompt attached to a student-facing story
 * activity, surfaced inside
 * {@link StudentStoryActivityResponse#questions()}.
 *
 * <h2>{@link #optionsPayload}</h2>
 * <p>Opaque JSON document encoding the multiple-choice options and
 * which one is correct. Emitted verbatim from the underlying
 * {@code story_questions.options_payload} column — the front-end is
 * the canonical schema owner. Yes, the correct answer is on the wire:
 * the platform-managed activity library does not gate XP on answer
 * correctness (see the idempotency note on
 * {@link StudentActivityCompletionResponse}), and exposing the answer
 * lets the client render the post-submit "explanation" overlay
 * without a second round trip. Anti-cheat for graded assessments
 * lives elsewhere in the codebase (the quiz / assessment
 * engine).</p>
 *
 * <p>{@link #explanation} is shown to the learner after they submit
 * an answer (regardless of correctness) so they can learn from the
 * prompt even when they got it right. {@link #sortOrder} preserves
 * the author's canonical question order; the front-end may
 * optionally shuffle on each play.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on {@link #id},
 * {@link #questionText} and {@link #optionsPayload} — they map to NOT
 * NULL schema columns and are required by the rendering and grading
 * paths (a story question without options is structurally meaningless).
 * {@link #explanation} and {@link #sortOrder} may legitimately be
 * {@code null} when the author left them unset.</p>
 *
 * @param id              database-assigned primary key; never
 *                        {@code null}
 * @param questionText    prompt the learner reads; never
 *                        {@code null}
 * @param optionsPayload  opaque JSON of multiple-choice options +
 *                        correct-answer marker; never {@code null}
 * @param explanation     post-submit explanation; nullable
 * @param sortOrder       canonical authoring order; nullable when
 *                        the author did not pin a sort position
 */
public record StudentStoryQuestionResponse(
        Long id,
        String questionText,
        String optionsPayload,
        String explanation,
        Integer sortOrder) {

    /**
     * Defensive canonical constructor: enforces non-null on the three
     * required schema columns ({@code id}, {@code questionText},
     * {@code optionsPayload}) so a malformed question cannot reach the
     * wire format and break the front-end multiple-choice rendering or
     * post-submit explanation overlay.
     */
    public StudentStoryQuestionResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(questionText, "questionText must not be null");
        Objects.requireNonNull(optionsPayload, "optionsPayload must not be null");
    }
}
