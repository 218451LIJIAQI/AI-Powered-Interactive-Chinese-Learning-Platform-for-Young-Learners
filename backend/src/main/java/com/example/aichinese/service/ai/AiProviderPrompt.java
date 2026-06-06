package com.example.aichinese.service.ai;

import java.util.List;
import java.util.Objects;

public record AiProviderPrompt(
        String featureKey,
        String systemInstruction,
        String userPrompt,
        String languagePreference,
        int maxOutputTokens,
        boolean expectJson,
        List<AiMessage> messageHistory) {

    public AiProviderPrompt {
        featureKey = requireNonBlank(featureKey, "featureKey");
        systemInstruction = requireNonBlank(systemInstruction, "systemInstruction");
        userPrompt = requireNonBlank(userPrompt, "userPrompt");
        languagePreference = requireNonBlank(languagePreference, "languagePreference");
        if (maxOutputTokens <= 0) {
            throw new IllegalArgumentException("maxOutputTokens must be > 0, got " + maxOutputTokens);
        }
        Objects.requireNonNull(messageHistory, "messageHistory");
        messageHistory = List.copyOf(messageHistory);
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return value;
    }

    public record AiMessage(String role, String content) {

        public AiMessage {
            role = requireNonBlank(role, "role");
            Objects.requireNonNull(content, "content");
        }
    }
}
