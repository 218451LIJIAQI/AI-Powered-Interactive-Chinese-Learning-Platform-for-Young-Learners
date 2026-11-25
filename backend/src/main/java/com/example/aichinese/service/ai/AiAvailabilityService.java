package com.example.aichinese.service.ai;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.AiProperties;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AiAvailabilityService {

    public static final String FRIENDLY_UNAVAILABLE_MESSAGE =
            "AI tutor is currently unavailable. Please try again later.";

    private static final String DISABLED_MESSAGE =
            "AI provider is disabled in the current backend configuration.";

    private static final String UNSUPPORTED_PROVIDER_MESSAGE =
            "AI provider is not supported by the current backend configuration.";

    private static final String MISSING_API_KEY_MESSAGE =
            "AI API key is missing in the current backend configuration.";

    private static final String MISSING_MODEL_MESSAGE =
            "AI model name is missing in the current backend configuration.";

    private static final String INVALID_BASE_URL_MESSAGE =
            "AI API base URL is missing or invalid in the current backend configuration.";

    private final AiProperties aiProperties;

    /**
     * Whether the AI provider can answer. When it cannot, {@code message} is the configuration reason, a technical
     * detail only written to the admin AI usage log. Handwriting uses the general provider's image input.
     * Pronunciation uses its own provider and can be configured independently.
     */
    public record Availability(
            boolean available,
            String provider,
            String model,
            String message,
            boolean imageAnalysisAvailable,
            boolean speechAnalysisAvailable) {

        public static Availability available(
                String provider, String model, boolean imageAnalysisAvailable, boolean speechAnalysisAvailable) {
            return new Availability(true, provider, model, null, imageAnalysisAvailable, speechAnalysisAvailable);
        }

        public static Availability unavailable(String provider, String model, String message) {
            return new Availability(false, provider, model, message, false, false);
        }
    }

    public Availability getAvailability() {
        String provider = aiProperties.normalizedProvider();
        String model = aiProperties.getModelName();

        if (!aiProperties.isEnableRealProvider()) {
            return Availability.unavailable(provider, model, DISABLED_MESSAGE);
        }
        boolean speech = aiProperties.getPronunciation().configured();
        String reason = unavailableReason(provider, model);
        if (reason != null) {
            return new Availability(false, provider, model, reason, false, speech);
        }
        return Availability.available(provider, model, AiProperties.OPENAI.equals(provider), speech);
    }

    private String unavailableReason(String provider, String model) {
        if (!AiProperties.SUPPORTED_PROVIDERS.contains(provider)) {
            return UNSUPPORTED_PROVIDER_MESSAGE;
        }
        if (aiProperties.getApiKey() == null) {
            return MISSING_API_KEY_MESSAGE;
        }
        if (model == null) {
            return MISSING_MODEL_MESSAGE;
        }
        if (aiProperties.normalizedApiBaseUrl() == null) {
            return INVALID_BASE_URL_MESSAGE;
        }
        return null;
    }
}
