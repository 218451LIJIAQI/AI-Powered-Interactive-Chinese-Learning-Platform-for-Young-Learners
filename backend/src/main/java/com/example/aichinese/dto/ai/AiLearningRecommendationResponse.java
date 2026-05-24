package com.example.aichinese.dto.ai;

import java.util.List;

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
 */
public record AiLearningRecommendationResponse(
        String recommendedNextStep,
        String recommendedLessonOrActivity,
        String weakAreaSummary,
        String revisionSuggestion,
        String shortEncouragement,
        List<String> suggestedPracticeTypes,
        String status) {
}
