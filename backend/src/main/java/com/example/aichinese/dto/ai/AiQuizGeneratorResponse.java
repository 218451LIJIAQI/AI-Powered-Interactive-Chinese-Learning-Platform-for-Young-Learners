package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured AI quiz generator response returned by
 * {@code POST /api/v1/ai/quiz-generator}.
 *
 * <p>Carries the generated quiz scaffold ({@link #title},
 * {@link #topic}, {@link #level}, {@link #category}) plus the
 * {@link #questions} list (capped at 10; see
 * {@link AiQuizGeneratorRequest#numberOfQuestions}).</p>
 *
 * <h2>{@link #usageMode}</h2>
 * <p>Echo of the request's {@code targetRoleContext}
 * ({@code "TEACHER"} / {@code "STUDENT"}) — used by the
 * front-end to branch the post-generation UX:</p>
 * <ul>
 *   <li>Teacher mode: render with "Save as quiz" CTA that
 *       pre-fills the {@code CreateQuizRequest} +
 *       {@code CreateQuizQuestionRequest} flow with the AI
 *       output for human review.</li>
 *   <li>Student mode: render as a transient self-quiz; nothing
 *       is persisted server-side beyond the
 *       {@link com.example.aichinese.entity.AiUsageLog} row.</li>
 * </ul>
 *
 * <p>The embedded {@link AiGeneratedQuizQuestionResponse} rows
 * carry the same shape as the persisted quiz question structure
 * so a teacher accepting the AI output gets a near-direct mapping
 * to {@code CreateQuizQuestionRequest}.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Defensive immutability</h2>
 * <p>The canonical constructor wraps {@link #questions} in
 * {@link List#copyOf(java.util.Collection) List.copyOf} so the
 * service-layer {@code ArrayList} that accumulates parsed AI
 * questions cannot leak through and be mutated after the response
 * is built. A {@code null} input is normalised to an empty list
 * so the wire format always emits {@code []} rather than
 * {@code null} and the front-end never has to branch on
 * {@code null}. {@link List#copyOf} short-circuits on
 * already-immutable inputs ({@link List#of()}) so the typical
 * fallback hot path allocates nothing.</p>
 */
@JsonPropertyOrder({ "title", "topic", "level", "category", "usageMode", "questions", "status" })
public record AiQuizGeneratorResponse(
        String title,
        String topic,
        String level,
        String category,
        String usageMode,
        List<AiGeneratedQuizQuestionResponse> questions,
        String status) {

    /**
     * Defensive canonical constructor: wraps {@link #questions}
     * in an immutable copy so external mutation of the originally
     * supplied list cannot leak into the already-serialised
     * response. A {@code null} input is normalised to
     * {@link List#of()}.
     */
    public AiQuizGeneratorResponse {
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
