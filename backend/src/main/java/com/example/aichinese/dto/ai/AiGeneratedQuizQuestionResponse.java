package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One AI-generated question row inside
 * {@link AiQuizGeneratorResponse#questions()}.
 *
 * <p>Mirrors the shape of the manual-authoring
 * {@code CreateQuizQuestionRequest} closely so a teacher
 * accepting the AI output can paste the row into the authoring
 * form with minimal mapping. {@link #questionType} carries a
 * string from the
 * {@link com.example.aichinese.entity.enums.QuizQuestionType}
 * vocabulary; {@link #options} is populated for option-based
 * types and empty for free-text / structured-payload types.</p>
 *
 * <p>{@link #correctAnswer} is plain text — for option-based
 * questions, it is the literal correct option string (the
 * front-end matches it back to {@link #options} to flag the
 * correct row when materialising into
 * {@code QuizOptionRequest} children).</p>
 *
 * <h2>Scaffolding extras</h2>
 * <p>{@link #pinyin} and {@link #translation} are AI-supplied
 * Mandarin-learner aids — not present on the manual-authoring
 * DTO. They let the front-end show "What does this question say
 * in English?" before the teacher commits the question, helping
 * the teacher spot AI translation errors.</p>
 *
 * <p>{@link #explanation} carries the post-submission rationale
 * (same as {@code QuizAttemptResultQuestionResponse#explanation}).</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Defensive immutability</h2>
 * <p>The canonical constructor wraps {@link #options} in
 * {@link List#copyOf(java.util.Collection) List.copyOf} so the
 * service-layer {@code ArrayList} that builds the question pool
 * cannot leak through and be mutated after the response is built.
 * A {@code null} input is normalised to an empty list so the wire
 * format always emits {@code []} rather than {@code null}, which
 * matches the front-end's "iterate options" expectation.
 * {@link List#copyOf} short-circuits on already-immutable inputs
 * ({@link List#of()}, {@code Stream.toList()}) so the typical
 * hot path allocates nothing.</p>
 */
@JsonPropertyOrder({
        "questionText",
        "questionType",
        "options",
        "correctAnswer",
        "explanation",
        "pinyin",
        "translation"
})
public record AiGeneratedQuizQuestionResponse(
        String questionText,
        String questionType,
        List<String> options,
        String correctAnswer,
        String explanation,
        String pinyin,
        String translation) {

    /**
     * Defensive canonical constructor: wraps {@link #options} in
     * an immutable copy so external mutation of the originally
     * supplied list cannot leak into the already-serialised
     * response. A {@code null} input is normalised to
     * {@link List#of()}.
     */
    public AiGeneratedQuizQuestionResponse {
        options = options == null ? List.of() : List.copyOf(options);
    }
}
