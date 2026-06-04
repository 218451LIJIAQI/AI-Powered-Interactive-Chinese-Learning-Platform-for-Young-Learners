package com.example.aichinese.service.ai;

@FunctionalInterface
public interface AiProviderClient {

    AiProviderResult generateText(AiProviderPrompt prompt);
}
