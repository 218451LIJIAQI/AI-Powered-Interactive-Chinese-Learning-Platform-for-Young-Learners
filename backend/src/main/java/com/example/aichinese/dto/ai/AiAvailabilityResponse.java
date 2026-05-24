package com.example.aichinese.dto.ai;

/**
 * AI provider health snapshot returned by the AI availability
 * probe (typically {@code GET /api/v1/ai/availability}).
 *
 * <p>Drives the front-end's "AI features available" / "AI is
 * temporarily unavailable" banner. The platform's AI features are
 * pluggable behind a {@code AiProviderClient} interface (today
 * backed by an OpenAI-compatible provider); this DTO surfaces
 * <em>capability flags</em> so the UI can selectively hide
 * features that the current provider configuration does not
 * support (e.g. hide pronunciation analysis when the audio
 * pipeline is disabled).</p>
 *
 * <h2>Capability flags</h2>
 * <ul>
 *   <li>{@link #available} — global flag; when {@code false}, all
 *       AI surfaces are gated regardless of the per-capability
 *       flags.</li>
 *   <li>{@link #textGenerationAvailable} — text-only features
 *       (vocabulary helper, sentence practice, quiz/story
 *       generation, learning recommendation, chat).</li>
 *   <li>{@link #audioAnalysisAvailable} — pronunciation feedback
 *       (requires audio upload &rarr; transcription pipeline).</li>
 *   <li>{@link #imageAnalysisAvailable} — image-aware features
 *       (currently reserved for future expansion).</li>
 * </ul>
 *
 * <p>{@link #provider} and {@link #model} are surfaced for admin
 * diagnostics. {@link #status} is a short token
 * ({@code "ONLINE"}, {@code "DEGRADED"}, {@code "UNAVAILABLE"})
 * and {@link #message} is the human-readable explanation for the
 * banner. All boolean fields are primitive so the JSON wire
 * format always emits {@code true}/{@code false}.</p>
 */
public record AiAvailabilityResponse(
        boolean available,
        String provider,
        String model,
        String status,
        String message,
        boolean textGenerationAvailable,
        boolean audioAnalysisAvailable,
        boolean imageAnalysisAvailable) {
}
