package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One AI-generated comprehension question inside
 * {@link AiStoryGeneratorResponse#comprehensionQuestions()}.
 *
 * <p>Always multiple-choice for the story-comprehension surface
 * (simpler shape than the more general
 * {@link AiGeneratedQuizQuestionResponse}). {@link #options} is
 * the answer pool; {@link #answer} is the literal correct option
 * string (the front-end matches it back to {@link #options} to
 * flag the right answer once the student submits).</p>
 *
 * <p>{@link #explanation} is shown after the student picks an
 * answer, regardless of correctness — the AI explains in the
 * student's preferred language why the right answer is right,
 * grounded in the surrounding story body. This is the same
 * "explain after submit" pattern documented on
 * {@code QuizAttemptResultQuestionResponse}.</p>
 *
 * <p>Story comprehension does not award XP — it is a pure
 * self-assessment surface for reading practice. Anti-cheat
 * concerns therefore do not apply (cf. the
 * {@code QuizOptionResponse} anti-cheat note); the answer is on
 * the wire because the student already trusted themselves to
 * read the story honestly.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Defensive immutability</h2>
 * <p>The canonical constructor wraps {@link #options} in
 * {@link List#copyOf(java.util.Collection) List.copyOf} so the
 * service-layer {@code ArrayList} that builds the answer pool
 * cannot leak through and be mutated after the response is built.
 * A {@code null} input is normalised to an empty list so the wire
 * format always emits {@code []} rather than {@code null}.</p>
 */
@JsonPropertyOrder({ "question", "options", "answer", "explanation" })
public record AiStoryComprehensionQuestionResponse(
        String question,
        List<String> options,
        String answer,
        String explanation) {

    /**
     * Defensive canonical constructor: wraps {@link #options} in
     * an immutable copy so external mutation of the originally
     * supplied list cannot leak into the already-serialised
     * response. A {@code null} input is normalised to
     * {@link List#of()}.
     */
    public AiStoryComprehensionQuestionResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
