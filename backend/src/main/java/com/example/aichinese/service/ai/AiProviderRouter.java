package com.example.aichinese.service.ai;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

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
            default -> noOpAiProviderClient;
        };
    }

    private String normalizedProvider() {
        if (aiProperties.getProvider() == null || aiProperties.getProvider().isBlank()) {
            return "deepseek";
        }
        return aiProperties.getProvider().trim().toLowerCase(java.util.Locale.ROOT);
    }
}
