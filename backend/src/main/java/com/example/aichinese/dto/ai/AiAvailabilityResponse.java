package com.example.aichinese.dto.ai;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * diagnostics. {@link #status} is a short token (one of the
 * {@code STATUS_*} constants below) and {@link #message} is the
 * human-readable explanation for the banner. All boolean fields
 * are primitive so the JSON wire format always emits
 * {@code true}/{@code false}.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder}
 * so upstream Jackson upgrades cannot accidentally reshuffle the
 * wire format and break consumers (snapshot tests, log
 * fingerprints, frontend code that pins on field positions).</p>
 */
@JsonPropertyOrder({
        "available",
        "provider",
        "model",
        "status",
        "message",
        "textGenerationAvailable",
        "audioAnalysisAvailable",
        "imageAnalysisAvailable"
})
public record AiAvailabilityResponse(
        boolean available,
        String provider,
        String model,
        String status,
        String message,
        boolean textGenerationAvailable,
        boolean audioAnalysisAvailable,
        boolean imageAnalysisAvailable) {

    /** Status token emitted when the provider is fully configured and reachable. */
    public static final String STATUS_AVAILABLE = "AVAILABLE";

    /** Status token emitted when the provider is disabled or mis-configured. */
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    /**
     * Builds the canonical "text generation only" snapshot
     * surfaced when the provider is healthy. Mirrors the platform's
     * current pluggable-AI capability matrix (audio and image
     * features are reserved for a future provider release).
     *
     * @param provider provider identifier (e.g. {@code "deepseek"},
     *                 {@code "openai-compatible"}); not validated
     *                 here — callers normalise upstream
     * @param model    model identifier (e.g. {@code "gpt-4o-mini"})
     * @param message  human-readable banner text shown to admins
     */
    public static AiAvailabilityResponse textGenerationAvailable(String provider, String model, String message) {
        return new AiAvailabilityResponse(
                true,
                provider,
                model,
                STATUS_AVAILABLE,
                message,
                true,
                false,
                false);
    }

    /**
     * Builds the canonical "all features gated" snapshot returned
     * when the provider is disabled or mis-configured. Every
     * capability flag is forced to {@code false} so the front-end
     * cannot accidentally render an AI surface for an offline
     * provider, regardless of the {@link #available} short-circuit.
     *
     * @param provider best-effort provider identifier (may be
     *                 {@code null} when configuration is missing)
     * @param model    best-effort model identifier (may be
     *                 {@code null} when configuration is missing)
     * @param message  human-readable banner text describing why the
     *                 provider is unavailable
     */
    public static AiAvailabilityResponse unavailable(String provider, String model, String message) {
        return new AiAvailabilityResponse(
                false,
                provider,
                model,
                STATUS_UNAVAILABLE,
                message,
                false,
                false,
                false);
    }
}
