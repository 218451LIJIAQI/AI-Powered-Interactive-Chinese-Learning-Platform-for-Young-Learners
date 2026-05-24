package com.example.aichinese.dto.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Structured AI learning recommendation returned by
 * {@code POST /api/v1/ai/learning-recommendations}.
 *
 * <p>Gives the student a coherent next-step plan rather than just
 * the bare CTA carried on
 * {@link com.example.aichinese.dto.dashboard.StudentDashboardResponse#recommendedNextStep()}
 * (which is a server-rule-derived label). This DTO comes from
 * the AI taking the holistic context into account.</p>
 *
 * <h2>Multi-faceted recommendation</h2>
 * <ul>
 *   <li>{@link #recommendedNextStep} — the headline directive
 *       (e.g. "Review pronunciation of tones 3 and 4").</li>
 *   <li>{@link #recommendedLessonOrActivity} — concrete pointer
 *       to the lesson / activity to do next.</li>
 *   <li>{@link #weakAreaSummary} — paragraph summarising the
 *       AI's read of the student's struggles.</li>
 *   <li>{@link #revisionSuggestion} — what to revisit before
 *       moving forward.</li>
 *   <li>{@link #suggestedPracticeTypes} — a short list of
 *       practice modes (flashcards, sentence practice, listening,
 *       etc.) the student should focus on.</li>
 *   <li>{@link #shortEncouragement} — the motivational close.</li>
 * </ul>
 *
 * <p>All text fields are returned in the student's preferred
 * language. {@link #status} mirrors the
 * {@link AiChatResponse#status} convention.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Defensive immutability</h2>
 * <p>The canonical constructor wraps {@link #suggestedPracticeTypes}
 * in {@link List#copyOf(java.util.Collection) List.copyOf} so any
 * upstream mutable list cannot leak through and be mutated after
 * the response is built. A {@code null} input is normalised to an
 * empty list so the wire format always emits {@code []} and the
 * front-end never has to branch on {@code null}.
 * {@link List#copyOf} short-circuits on already-immutable inputs
 * ({@link List#of()}, {@code Stream.toList()}) so the typical
 * hot path — the service's
 * {@code AiService.parseLearningRecommendation} pass that hands
 * back either a {@code List.of(...)} fallback or a
 * {@code Stream.toList()} of bounded items — allocates nothing.</p>
 */
@JsonPropertyOrder({
        "recommendedNextStep",
        "recommendedLessonOrActivity",
        "weakAreaSummary",
        "revisionSuggestion",
        "shortEncouragement",
        "suggestedPracticeTypes",
        "status"
})
public record AiLearningRecommendationResponse(
        String recommendedNextStep,
        String recommendedLessonOrActivity,
        String weakAreaSummary,
        String revisionSuggestion,
        String shortEncouragement,
        List<String> suggestedPracticeTypes,
        String status) {

    /**
     * Defensive canonical constructor: wraps
     * {@link #suggestedPracticeTypes} in an immutable copy so
     * external mutation of the originally supplied list cannot
     * leak into the already-serialised response. A {@code null}
     * input is normalised to {@link List#of()}.
     */
    public AiLearningRecommendationResponse {
        suggestedPracticeTypes = suggestedPracticeTypes == null
                ? List.of()
                : List.copyOf(suggestedPracticeTypes);
    }
}
