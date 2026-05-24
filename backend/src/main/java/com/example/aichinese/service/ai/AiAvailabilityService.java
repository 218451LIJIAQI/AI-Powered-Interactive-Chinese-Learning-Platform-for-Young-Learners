package com.example.aichinese.service.ai;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.util.TextUtils;

/**
 * Computes the AI subsystem availability flags exposed by
 * {@code GET /api/v1/ai/availability} for the front-end's
 * "AI features ready / unready" banner and consumed internally by
 * {@link com.example.aichinese.service.AiService} before every
 * provider call.
 *
 * <p>Returns {@link AiAvailabilityResponse#unavailable unavailable}
 * when any of the following hold (checked in order, fail-fast):</p>
 * <ol>
 *   <li>The {@code app.ai.enable-real-provider} toggle is off.</li>
 *   <li>The configured provider is not one of the values in
 *       {@link AiProviderSupport#SUPPORTED_PROVIDERS}.</li>
 *   <li>The API key is missing.</li>
 *   <li>The model name is missing.</li>
 *   <li>The base URL is missing or fails the runtime URL rules
 *       enforced by {@link AiProviderSupport#isValidRuntimeBaseUrl}
 *       — HTTPS only, except for loopback hosts which may use HTTP
 *       (integration tests, local Ollama-style dev servers).</li>
 * </ol>
 *
 * <p>The detailed reason is included in the response so admins can
 * diagnose mis-configuration without server-side log access; the
 * provider/model identifiers are surfaced even on failure so the
 * dashboard can show which configured combination is broken.</p>
 *
 * <p>The pronunciation / writing-feedback capability flags are
 * statically wired to {@code false} because no current provider
 * supports those modalities; the {@link #PRONUNCIATION_UNAVAILABLE_MESSAGE}
 * / {@link #WRITING_UNAVAILABLE_MESSAGE} constants are surfaced to
 * the front-end so the same canonical wording appears wherever the
 * features are accessed.</p>
 *
 * <p>This service is stateless and thread-safe — {@link AiProperties}
 * is bound at boot, and per-request mutation is prohibited by the
 * {@link com.example.aichinese.config.AiProductionGuard} validation
 * pass.</p>
 */
@Service
public class AiAvailabilityService {

    /**
     * Learner-safe message returned to the response body whenever an
     * AI feature falls back due to a provider/configuration outage.
     * Kept short, blameless, and self-routing ("try again later") so
     * children are not confronted with technical detail.
     */
    public static final String FRIENDLY_UNAVAILABLE_MESSAGE =
            "AI tutor is currently unavailable. Please try again later.";

    /**
     * Canonical reason surfaced for the audio-analysis modality.
     * Re-used by {@link com.example.aichinese.service.AiService} so
     * the wording stays consistent everywhere pronunciation feedback
     * is gated.
     */
    public static final String PRONUNCIATION_UNAVAILABLE_MESSAGE =
            "Pronunciation feedback is currently unavailable because the selected AI provider does not support audio analysis yet.";

    /**
     * Canonical reason surfaced for the image-analysis modality.
     * Mirrors {@link #PRONUNCIATION_UNAVAILABLE_MESSAGE} for handwriting feedback.
     */
    public static final String WRITING_UNAVAILABLE_MESSAGE =
            "Writing feedback is currently unavailable because the selected AI provider does not support image analysis yet.";

    static final String AVAILABLE_MESSAGE = "AI provider is configured.";

    static final String DISABLED_MESSAGE =
            "AI provider is disabled in the current backend configuration.";

    static final String UNSUPPORTED_PROVIDER_MESSAGE =
            "AI provider is not supported by the current backend configuration.";

    static final String MISSING_API_KEY_MESSAGE =
            "AI API key is missing in the current backend configuration.";

    static final String MISSING_MODEL_MESSAGE =
            "AI model name is missing in the current backend configuration.";

    static final String INVALID_BASE_URL_MESSAGE =
            "AI API base URL is missing or invalid in the current backend configuration.";

    private final AiProperties aiProperties;

    public AiAvailabilityService(AiProperties aiProperties) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
    }

    /**
     * Snapshots the current AI provider configuration into the wire
     * shape consumed by the front-end's readiness banner. Never throws
     * — every error path is encoded as an
     * {@link AiAvailabilityResponse#unavailable unavailable} response
     * so the caller (controller or {@code AiService}) can render the
     * banner / fallback without additional defensive code.
     */
    public AiAvailabilityResponse getAvailability() {
        String provider = normalizedProvider();
        String model = TextUtils.trimToNull(aiProperties.getModelName());

        if (!aiProperties.isEnableRealProvider()) {
            return AiAvailabilityResponse.unavailable(provider, model, DISABLED_MESSAGE);
        }
        if (!AiProviderSupport.isSupportedProvider(provider)) {
            return AiAvailabilityResponse.unavailable(provider, model, UNSUPPORTED_PROVIDER_MESSAGE);
        }
        if (TextUtils.trimToNull(aiProperties.getApiKey()) == null) {
            return AiAvailabilityResponse.unavailable(provider, model, MISSING_API_KEY_MESSAGE);
        }
        if (model == null) {
            return AiAvailabilityResponse.unavailable(provider, null, MISSING_MODEL_MESSAGE);
        }
        if (!AiProviderSupport.isValidRuntimeBaseUrl(aiProperties.getApiBaseUrl())) {
            return AiAvailabilityResponse.unavailable(provider, model, INVALID_BASE_URL_MESSAGE);
        }

        return AiAvailabilityResponse.textGenerationAvailable(provider, model, AVAILABLE_MESSAGE);
    }

    /**
     * Returns the canonical provider identifier — always non-null —
     * surfaced as part of {@link AiAvailabilityResponse#provider()} and
     * re-used by {@link com.example.aichinese.service.AiService} when
     * recording audit-log entries for the pronunciation / writing
     * fallback paths so the log row attributes usage to the same
     * provider name that the availability probe reports.
     */
    public String normalizedProvider() {
        return AiProviderSupport.normalizeProvider(aiProperties.getProvider());
    }
}
