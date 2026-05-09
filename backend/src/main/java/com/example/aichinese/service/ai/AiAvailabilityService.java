package com.example.aichinese.service.ai;

import java.net.URI;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;

@Service
public class AiAvailabilityService {

    public static final String FRIENDLY_UNAVAILABLE_MESSAGE =
            "AI tutor is currently unavailable. Please try again later.";

    public static final String PRONUNCIATION_UNAVAILABLE_MESSAGE =
            "Pronunciation feedback is currently unavailable because the selected AI provider does not support audio analysis yet.";

    public static final String WRITING_UNAVAILABLE_MESSAGE =
            "Writing feedback is currently unavailable because the selected AI provider does not support image analysis yet.";

    private final AiProperties aiProperties;

    public AiAvailabilityService(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    public AiAvailabilityResponse getAvailability() {
        String provider = normalizedProvider();
        String model = trimmed(aiProperties.getModelName());
        if (!aiProperties.isEnableRealProvider()) {
            return new AiAvailabilityResponse(false, provider, model, "UNAVAILABLE",
                    "AI provider is disabled in the current backend configuration.");
        }

        if (trimmed(aiProperties.getApiKey()) == null) {
            return new AiAvailabilityResponse(false, provider, model, "UNAVAILABLE",
                    "AI API key is missing in the current backend configuration.");
        }

        if (model == null) {
            return new AiAvailabilityResponse(false, provider, null, "UNAVAILABLE",
                    "AI model name is missing in the current backend configuration.");
        }

        if (!isValidUrl(trimmed(aiProperties.getApiBaseUrl()))) {
            return new AiAvailabilityResponse(false, provider, model, "UNAVAILABLE",
                    "AI API base URL is missing or invalid in the current backend configuration.");
        }

        return new AiAvailabilityResponse(true, provider, model, "AVAILABLE", "AI provider is configured.");
    }

    public boolean isAvailable() {
        return getAvailability().available();
    }

    public String normalizedProvider() {
        String value = trimmed(aiProperties.getProvider());
        return value == null ? "deepseek" : value.toLowerCase(Locale.ROOT);
    }

    private boolean isValidUrl(String value) {
        if (value == null) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            return uri.getScheme() != null && uri.getHost() != null;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String trimmed(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
