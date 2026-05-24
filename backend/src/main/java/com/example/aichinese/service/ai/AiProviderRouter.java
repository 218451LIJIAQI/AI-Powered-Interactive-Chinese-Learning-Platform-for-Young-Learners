package com.example.aichinese.service.ai;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

/**
 * Selects the active {@link AiProviderClient} for every AI feature
 * call.
 *
 * <p>The decision is taken per-call (not once at startup) so a runtime
 * configuration change to {@code app.ai.enable-real-provider} or
 * {@code app.ai.provider} is picked up without a restart. When the
 * real-provider toggle is off, every request gets the
 * {@link NoOpAiProviderClient} which fails cleanly so feature
 * fallbacks kick in. When the toggle is on, the configured provider
 * name is matched against the supported set
 * ({@code deepseek} / {@code openai-compatible}); an unknown value
 * throws so a typo does not silently degrade to no-op behaviour.</p>
 */
@Component
public class AiProviderRouter {

    private final AiProperties aiProperties;
    private final OpenAiCompatibleAiProviderClient openAiCompatibleAiProviderClient;
    private final NoOpAiProviderClient noOpAiProviderClient;

    public AiProviderRouter(
            AiProperties aiProperties,
            OpenAiCompatibleAiProviderClient openAiCompatibleAiProviderClient,
            NoOpAiProviderClient noOpAiProviderClient) {
        this.aiProperties = aiProperties;
        this.openAiCompatibleAiProviderClient = openAiCompatibleAiProviderClient;
        this.noOpAiProviderClient = noOpAiProviderClient;
    }

    public AiProviderClient resolveClient() {
        if (!aiProperties.isEnableRealProvider()) {
            return noOpAiProviderClient;
        }

        return switch (normalizedProvider()) {
            case "deepseek", "openai-compatible", "openai_compatible" -> openAiCompatibleAiProviderClient;
            default -> throw new IllegalStateException(
                    "AI_PROVIDER is not supported while AI_ENABLE_REAL_PROVIDER=true.");
        };
    }

    private String normalizedProvider() {
        if (aiProperties.getProvider() == null || aiProperties.getProvider().isBlank()) {
            return "deepseek";
        }
        return aiProperties.getProvider().trim().toLowerCase(java.util.Locale.ROOT);
    }
}
