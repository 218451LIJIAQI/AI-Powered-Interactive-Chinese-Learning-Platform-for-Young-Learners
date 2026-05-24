package com.example.aichinese.service.ai;

import java.net.URI;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.dto.ai.AiAvailabilityResponse;
import com.example.aichinese.util.TextUtils;

/**
 * Computes the AI subsystem availability flags exposed by
 * {@code GET /api/v1/ai/availability} for the front-end's
 * "AI features ready / unready" banner.
 *
 * <p>Returns {@code unavailable} when any of the following hold:
 * the real-provider toggle is off; the configured provider is
 * unsupported; the API key / model name is missing; or the configured
 * base URL is not a valid HTTPS URL (loopback HTTP allowed for
 * integration tests). The detailed reason is included in the response
 * so admins can diagnose mis-configuration without server-side log
 * access.</p>
 *
 * <p>The pronunciation / writing-feedback flags are statically wired
 * to {@code false} because no current provider supports those
 * modalities; the {@link #PRONUNCIATION_UNAVAILABLE_MESSAGE} /
 * {@link #WRITING_UNAVAILABLE_MESSAGE} constants are surfaced to the
 * front-end so the same canonical wording appears wherever the
 * features are accessed.</p>
 */
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
        String model = TextUtils.trimToNull(aiProperties.getModelName());
        if (!aiProperties.isEnableRealProvider()) {
            return unavailable(provider, model, "AI provider is disabled in the current backend configuration.");
        }

        if (!isSupportedProvider(provider)) {
            return unavailable(provider, model, "AI provider is not supported by the current backend configuration.");
        }

        if (TextUtils.trimToNull(aiProperties.getApiKey()) == null) {
            return unavailable(provider, model, "AI API key is missing in the current backend configuration.");
        }

        if (model == null) {
            return unavailable(provider, null, "AI model name is missing in the current backend configuration.");
        }

        if (!isValidProviderBaseUrl(TextUtils.trimToNull(aiProperties.getApiBaseUrl()))) {
            return unavailable(provider, model, "AI API base URL is missing or invalid in the current backend configuration.");
        }

        return new AiAvailabilityResponse(
                true,
                provider,
                model,
                "AVAILABLE",
                "AI provider is configured.",
                true,
                false,
                false);
    }

    private AiAvailabilityResponse unavailable(String provider, String model, String message) {
        return new AiAvailabilityResponse(false, provider, model, "UNAVAILABLE", message, false, false, false);
    }

    public String normalizedProvider() {
        String value = TextUtils.trimToNull(aiProperties.getProvider());
        String normalized = value == null ? "deepseek" : value.toLowerCase(Locale.ROOT);
        return "openai_compatible".equals(normalized) ? "openai-compatible" : normalized;
    }

    private boolean isSupportedProvider(String provider) {
        return "deepseek".equals(provider) || "openai-compatible".equals(provider);
    }

    private boolean isValidProviderBaseUrl(String value) {
        if (value == null) {
            return false;
        }

        try {
            URI uri = URI.create(value);
            if (uri.getScheme() == null || uri.getHost() == null) {
                return false;
            }
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            return "https".equals(scheme) || ("http".equals(scheme) && isLoopbackHost(uri.getHost()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean isLoopbackHost(String host) {
        String normalized = host == null ? "" : host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalized)
                || "127.0.0.1".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }
}
