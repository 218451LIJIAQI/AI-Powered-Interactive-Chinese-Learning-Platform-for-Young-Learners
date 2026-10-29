package com.example.aichinese.service.ai;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.util.TextUtils;

@Service
public class AiAvailabilityService {

    public static final String FRIENDLY_UNAVAILABLE_MESSAGE =
            "AI tutor is currently unavailable. Please try again later.";

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
        this.aiProperties = aiProperties;
    }

    public AiAvailabilityResponse getAvailability() {
        String provider = aiProperties.normalizedProvider();
        String model = TextUtils.trimToNull(aiProperties.getModelName());

        if (!aiProperties.isEnableRealProvider()) {
            return AiAvailabilityResponse.unavailable(provider, model, DISABLED_MESSAGE);
        }
        if (!AiProperties.SUPPORTED_PROVIDERS.contains(provider)) {
            return AiAvailabilityResponse.unavailable(provider, model, UNSUPPORTED_PROVIDER_MESSAGE);
        }
        if (TextUtils.trimToNull(aiProperties.getApiKey()) == null) {
            return AiAvailabilityResponse.unavailable(provider, model, MISSING_API_KEY_MESSAGE);
        }
        if (model == null) {
            return AiAvailabilityResponse.unavailable(provider, null, MISSING_MODEL_MESSAGE);
        }
        if (AiProviderSupport.validateAndNormalizeBaseUrl(aiProperties.getApiBaseUrl()) == null) {
            return AiAvailabilityResponse.unavailable(provider, model, INVALID_BASE_URL_MESSAGE);
        }

        // Only the OpenAI Responses client sends image input, and only OpenAI offers the speech-to-text endpoint;
        // the Chat Completions-compatible alternatives are text only.
        boolean openAi = AiProperties.OPENAI.equals(provider);
        boolean speech = openAi && TextUtils.trimToNull(aiProperties.getTranscriptionModel()) != null;
        return AiAvailabilityResponse.available(provider, model, AVAILABLE_MESSAGE, openAi, speech);
    }
}
