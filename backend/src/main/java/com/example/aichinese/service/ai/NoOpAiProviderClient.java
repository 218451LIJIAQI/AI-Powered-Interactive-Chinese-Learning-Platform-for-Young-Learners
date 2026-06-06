package com.example.aichinese.service.ai;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.example.aichinese.config.AiProperties;
import com.example.aichinese.util.TextUtils;

@Component
public class NoOpAiProviderClient implements AiProviderClient {

    private final AiProperties aiProperties;

    public NoOpAiProviderClient(AiProperties aiProperties) {
        this.aiProperties = Objects.requireNonNull(aiProperties, "aiProperties");
    }

    @Override
    public AiProviderResult generateText(AiProviderPrompt prompt) {
        Objects.requireNonNull(prompt, "prompt");
        return AiProviderResult.failure(
                AiProviderSupport.normalizeProvider(aiProperties.getProvider()),
                TextUtils.trimToNull(aiProperties.getModelName()),
                "AI provider is currently disabled or unavailable.");
    }
}
