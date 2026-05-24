package com.example.aichinese.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for the AI chat endpoint
 * ({@code POST /api/v1/ai/chat}) — the student sends one message
 * to the SparkBot AI tutor and receives a structured response.
 *
 * <h2>Session continuity</h2>
 * <p>{@link #sessionId} is the server-side
 * {@link com.example.aichinese.entity.AiChatSession} id that
 * groups a multi-turn conversation. {@code null} on the first
 * message of a new conversation; subsequent messages echo the id
 * the previous response returned via
 * {@link AiChatResponse#sessionId()}. Sessions are scoped to the
 * calling user, so the service rejects mismatched
 * {@link #sessionId} from another user with {@code 404}.</p>
 *
 * <h2>{@link #context} / {@link #relatedLessonId}</h2>
 * <p>Optional grounding hints to keep the AI tutor on-topic:</p>
 * <ul>
 *   <li>{@link #context} — free-form short snippet (capped at
 *       1000 chars) like a sentence the student is currently
 *       struggling with.</li>
 *   <li>{@link #relatedLessonId} — id of the lesson the student
 *       was viewing. The service looks up the lesson and feeds
 *       its title / topic into the system prompt so the AI's
 *       answers stay relevant to the current learning unit.</li>
 * </ul>
 *
 * <h2>{@link #languagePreference}</h2>
 * <p>Two-letter language code ({@code "en"} / {@code "ms"}) so
 * the tutor responds in the student's preferred study language;
 * defaults to the student's profile preference when omitted.</p>
 *
 * <p>{@link #message} is sanitised server-side via
 * {@code AiInputSanitizer} before being passed to the upstream
 * provider; rate-limited per user via the AI usage quota.</p>
 */
public record AiChatRequest(
        @NotBlank(message = "Message is required.")
        @Size(max = 2000, message = "Message must not exceed 2000 characters.")
        String message,
        @Size(max = 20, message = "Language preference must not exceed 20 characters.")
        String languagePreference,
        @Positive(message = "Session id must be positive.")
        Long sessionId,
        @Size(max = 1000, message = "Context must not exceed 1000 characters.")
        String context,
        @Positive(message = "Related lesson id must be positive.")
        Long relatedLessonId) {
}
