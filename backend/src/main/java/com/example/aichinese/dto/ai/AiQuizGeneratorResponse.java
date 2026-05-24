package com.example.aichinese.dto.ai;

import java.util.List;

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
 */
public record AiQuizGeneratorResponse(
        String title,
        String topic,
        String level,
        String category,
        String usageMode,
        List<AiGeneratedQuizQuestionResponse> questions,
        String status) {
}
