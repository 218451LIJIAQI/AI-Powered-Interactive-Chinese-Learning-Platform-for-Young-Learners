package com.example.aichinese.service.ai;

import java.util.Objects;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.util.TextUtils;

@Service
public class AiAvailabilityService {

    public static final String FRIENDLY_UNAVAILABLE_MESSAGE =
            "AI tutor is currently unavailable. Please try again later.";

    public static final String PRONUNCIATION_UNAVAILABLE_MESSAGE =
            "AI audio feedback is not supported by the configured model; your recording is saved for teacher review.";

    public static final String WRITING_UNAVAILABLE_MESSAGE =
            "AI handwriting feedback is not available right now; your image is saved for teacher review.";

    public static final String MEDIA_FEEDBACK_NOTICE =
            "Limited AI-assisted feedback for practice only. It is not a score or an official assessment.";

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

        // Only the OpenAI Responses client sends image input (GPT-5.6 Luna accepts images);
        // the Chat Completions-compatible alternatives are text only.
        boolean imageAnalysisAvailable = AiProviderSupport.OPENAI.equals(provider);
        return AiAvailabilityResponse.available(provider, model, AVAILABLE_MESSAGE, imageAnalysisAvailable);
    }

    public String normalizedProvider() {
        return AiProviderSupport.normalizeProvider(aiProperties.getProvider());
    }
}
