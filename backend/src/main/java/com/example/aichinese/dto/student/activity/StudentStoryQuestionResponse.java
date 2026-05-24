package com.example.aichinese.dto.student.activity;

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
 */
public record StudentStoryQuestionResponse(
        Long id,
        String questionText,
        String optionsPayload,
        String explanation,
        Integer sortOrder) {
}
