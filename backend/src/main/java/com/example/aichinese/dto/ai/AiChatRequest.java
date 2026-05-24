package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers that pin on field positions.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * {@link #message}, {@link #languagePreference} and
 * {@link #context} so Bean Validation operates on the canonical
 * form (a whitespace-only {@code message} correctly fails
 * {@link NotBlank @NotBlank} after trimming, the way the user
 * expects) and the service layer's
 * {@code AiInputSanitizer.sanitizeLong} pass is idempotent.
 * {@code null} is preserved so the {@link NotBlank @NotBlank}
 * validator renders the friendly "Message is required." message
 * rather than a low-level {@link NullPointerException}.</p>
 */
@JsonPropertyOrder({ "message", "languagePreference", "sessionId", "context", "relatedLessonId" })
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

    /**
     * Defensive canonical constructor: trims the three free-text
     * fields so Bean Validation operates on the canonical form and
     * the service layer's sanitisation calls are idempotent.
     * {@code null} is preserved so {@link NotBlank @NotBlank} can
     * render its friendly user-facing message rather than a
     * low-level {@link NullPointerException}. Numeric ids are not
     * normalised here — {@link Positive @Positive} handles them.
     */
    public AiChatRequest {
        if (message != null) {
            message = message.trim();
        }
        if (languagePreference != null) {
            languagePreference = languagePreference.trim();
        }
        if (context != null) {
            context = context.trim();
        }
    }
}
