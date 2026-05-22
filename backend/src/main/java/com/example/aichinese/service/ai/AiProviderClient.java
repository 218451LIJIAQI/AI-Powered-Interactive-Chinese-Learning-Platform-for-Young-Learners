package com.example.aichinese.service.ai;

public interface AiProviderClient {

    AiProviderResult generateText(AiProviderPrompt prompt);
}
