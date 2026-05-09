package com.example.aichinese.service.ai;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;

@Component
public class NoOpAiProviderClient implements AiProviderClient {

    private final AiProperties aiProperties;

    public NoOpAiProviderClient(AiProperties aiProperties) {
        this.aiProperties = aiProperties;
    }

    @Override
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        return AiProviderResult.failure(providerName(), aiProperties.getModelName(),
                "AI provider is currently disabled or unavailable.");
    }

    private String providerName() {
        return aiProperties.getProvider() == null || aiProperties.getProvider().isBlank()
                ? "deepseek"
                : aiProperties.getProvider().trim();
    }
}
