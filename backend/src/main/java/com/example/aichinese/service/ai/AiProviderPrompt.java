package com.example.aichinese.service.ai;

import java.util.List;

public record AiProviderPrompt(
        String featureKey,
        String systemInstruction,
        String userPrompt,
        String languagePreference,
        int maxOutputTokens,
        boolean expectJson,
        List<AiMessage> messageHistory) {

    public record AiMessage(String role, String content) {
    }
}
